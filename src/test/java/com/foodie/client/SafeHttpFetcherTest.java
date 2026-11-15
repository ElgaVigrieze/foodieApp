package com.foodie.client;

import com.foodie.exception.UnsafeUrlException;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SafeHttpFetcherTest {

    // ── Address classification (pure, no network) ────────────────────────────

    @ParameterizedTest(name = "{0} is blocked")
    @ValueSource(strings = {
            "127.0.0.1", "127.1.2.3", "0.0.0.0", "0.1.2.3",
            "10.0.0.1", "172.16.0.1", "172.31.255.255", "192.168.1.1",
            "169.254.169.254",                       // cloud metadata
            "100.64.0.1", "100.127.255.255",         // carrier-grade NAT
            "192.0.0.1", "192.0.2.1", "198.18.0.1", "198.51.100.1", "203.0.113.1",
            "224.0.0.1", "240.0.0.1", "255.255.255.255",
            "::1", "::", "fe80::1", "fec0::1", "fc00::1", "fd12:3456::1",
            "::ffff:10.0.0.1", "::ffff:127.0.0.1", "::ffff:169.254.169.254",  // IPv4-mapped IPv6
            "2001:db8::1", "64:ff9b::a00:1",
            "::10.0.0.1", "::a00:1", "2002:a00:1::1", "2001:0:4136:e378:8000:63bf:3fff:fdd2", "100::1"   // embed IPv4 / not routable
    })
    void blocksNonPublicAddresses(String literal) throws Exception {
        assertThat(SafeHttpFetcher.isPublicAddress(InetAddress.getByName(literal))).isFalse();
    }

    @ParameterizedTest(name = "{0} is allowed")
    @ValueSource(strings = {
            "8.8.8.8", "1.1.1.1", "93.184.216.34", "172.32.0.1", "172.15.255.255",
            "100.63.255.255", "100.128.0.1", "2606:4700:4700::1111", "2001:4860:4860::8888"
    })
    void allowsPublicAddresses(String literal) throws Exception {
        assertThat(SafeHttpFetcher.isPublicAddress(InetAddress.getByName(literal))).isTrue();
    }

    // ── URL validation ───────────────────────────────────────────────────────

    private final SafeHttpFetcher strict = new SafeHttpFetcher(2000, 2000);

    @ParameterizedTest(name = "rejects {0}")
    @ValueSource(strings = {
            "", "   ", "not a url", "ftp://example.com/file", "file:///etc/passwd", "javascript:alert(1)",
            "http://user:pass@example.com/", "https://example.com:8080/", "http://example.com:22/",
            "http://localhost/", "http://LOCALHOST./", "http://app.localhost/", "http://db.internal/",
            "http://metadata.google.internal/", "http://printer.local/", "http://router.lan/"
    })
    void rejectsUnsafeUrls(String url) {
        assertThatThrownBy(() -> strict.validate(url)).isInstanceOf(UnsafeUrlException.class);
    }

    @Test
    void rejectsNullUrl() {
        assertThatThrownBy(() -> strict.validate(null)).isInstanceOf(UnsafeUrlException.class);
    }

    @ParameterizedTest(name = "accepts {0}")
    @ValueSource(strings = {"https://example.com/recipe", "http://example.com/", "https://example.com:443/x?y=1"})
    void acceptsOrdinaryUrls(String url) {
        strict.validate(url); // no exception
    }

    // ── Fetching against a local server ──────────────────────────────────────

    private HttpServer server;
    private final AtomicInteger hits = new AtomicInteger();
    private String baseUrl;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/ok", ex -> respond(ex, 200, "hello recipe"));
        server.createContext("/big", ex -> respond(ex, 200, "x".repeat(SafeHttpFetcher.MAX_BODY_BYTES + 5000)));
        server.createContext("/missing", ex -> respond(ex, 404, "nope"));
        server.createContext("/loop", ex -> {
            ex.getResponseHeaders().add("Location", baseUrl + "/loop");
            respond(ex, 302, "");
        });
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    private void respond(com.sun.net.httpserver.HttpExchange ex, int status, String body) throws IOException {
        hits.incrementAndGet();
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        ex.sendResponseHeaders(status, status == 302 ? -1 : bytes.length);
        if (status != 302) ex.getResponseBody().write(bytes);
        ex.close();
    }

    /** Allows any port (the test server is on an ephemeral one) but keeps the real address rules. */
    private SafeHttpFetcher withRealAddressRules() {
        return new SafeHttpFetcher(2000, 2000, SafeHttpFetcher::isPublicAddress, port -> true);
    }

    @Test
    @DisplayName("a URL whose host is a loopback address is refused before any connection is made")
    void refusesLoopbackWithoutConnecting() {
        assertThatThrownBy(() -> withRealAddressRules().fetchText(baseUrl + "/ok", Map.of()))
                .isInstanceOf(UnsafeUrlException.class);
        assertThat(hits.get()).isZero();
    }

    @Test
    @DisplayName("a redirect to a non-allowed address is refused, not followed")
    void refusesRedirectToBlockedAddress() throws Exception {
        // Only 127.0.0.1 is allowed, so the server may be reached but the redirect target may not.
        server.createContext("/redirect", ex -> {
            ex.getResponseHeaders().add("Location", "http://127.0.0.2:" + server.getAddress().getPort() + "/ok");
            respond(ex, 302, "");
        });
        var onlyLoopbackOne = new SafeHttpFetcher(2000, 2000,
                a -> a.getHostAddress().equals("127.0.0.1"), port -> true);

        assertThatThrownBy(() -> onlyLoopbackOne.fetchText(baseUrl + "/redirect", Map.of()))
                .isInstanceOf(UnsafeUrlException.class);
        assertThat(hits.get()).isEqualTo(1); // the redirect response only; /ok on 127.0.0.2 never contacted
    }

    @Test
    void fetchesAllowedHost() throws Exception {
        var permissive = new SafeHttpFetcher(2000, 2000, a -> true, port -> true);
        assertThat(permissive.fetchText(baseUrl + "/ok", Map.of("Accept", "text/html")))
                .isEqualTo("hello recipe");
    }

    @Test
    void capsResponseSize() throws Exception {
        var permissive = new SafeHttpFetcher(2000, 2000, a -> true, port -> true);
        assertThat(permissive.fetchText(baseUrl + "/big", Map.of()))
                .hasSize(SafeHttpFetcher.MAX_BODY_BYTES);
    }

    @Test
    void httpErrorStatusBecomesIoException() {
        var permissive = new SafeHttpFetcher(2000, 2000, a -> true, port -> true);
        assertThatThrownBy(() -> permissive.fetchText(baseUrl + "/missing", Map.of()))
                .isInstanceOf(IOException.class).hasMessageContaining("404");
    }

    @Test
    void endlessRedirectLoopIsStopped() {
        var permissive = new SafeHttpFetcher(2000, 2000, a -> true, port -> true);
        assertThatThrownBy(() -> permissive.fetchText(baseUrl + "/loop", Map.of()))
                .isInstanceOf(Exception.class);
        assertThat(hits.get()).isLessThanOrEqualTo(SafeHttpFetcher.MAX_REDIRECTS + 2);
    }

    // ── Redirect hops get the same rules as the first URL ────────────────────

    @Test
    @DisplayName("a redirect to a non-standard port is refused")
    void refusesRedirectToDisallowedPort() {
        int serverPort = server.getAddress().getPort();
        server.createContext("/to-port", ex -> {
            ex.getResponseHeaders().add("Location", "http://127.0.0.1:1/internal");
            respond(ex, 302, "");
        });
        var onlyServerPort = new SafeHttpFetcher(2000, 2000, a -> true, port -> port == serverPort);

        assertThatThrownBy(() -> onlyServerPort.fetchText(baseUrl + "/to-port", Map.of()))
                .isInstanceOf(UnsafeUrlException.class).hasMessageContaining("Port");
        assertThat(hits.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("a redirect to a URL with embedded credentials is refused")
    void refusesRedirectWithCredentials() {
        int serverPort = server.getAddress().getPort();
        server.createContext("/to-creds", ex -> {
            ex.getResponseHeaders().add("Location", "http://user:secret@127.0.0.1:" + serverPort + "/ok");
            respond(ex, 302, "");
        });
        var permissive = new SafeHttpFetcher(2000, 2000, a -> true, port -> true);

        assertThatThrownBy(() -> permissive.fetchText(baseUrl + "/to-creds", Map.of()))
                .isInstanceOf(UnsafeUrlException.class).hasMessageContaining("credentials");
        assertThat(hits.get()).isEqualTo(1);
    }

    // ── The size cap really stops the download ───────────────────────────────

    @Test
    @DisplayName("an oversized body is cut off and the rest is not downloaded")
    void doesNotDownloadTheRestOfAHugeBody() throws Exception {
        AtomicLong sent = new AtomicLong();
        CountDownLatch handlerDone = new CountDownLatch(1);
        server.createContext("/huge", ex -> {
            hits.incrementAndGet();
            ex.sendResponseHeaders(200, 0); // chunked, unknown length
            byte[] chunk = new byte[64 * 1024];
            java.util.Arrays.fill(chunk, (byte) 'x');
            try (var out = ex.getResponseBody()) {
                for (int i = 0; i < 3200; i++) { // 200 MB if nobody stops us
                    out.write(chunk);
                    sent.addAndGet(chunk.length);
                }
            } catch (IOException clientClosed) {
                // expected: the fetcher dropped the connection
            } finally {
                handlerDone.countDown();
            }
        });
        var permissive = new SafeHttpFetcher(2000, 5000, a -> true, port -> true);

        String body = permissive.fetchText(baseUrl + "/huge", Map.of());

        assertThat(body).hasSize(SafeHttpFetcher.MAX_BODY_BYTES);
        assertThat(handlerDone.await(10, TimeUnit.SECONDS)).isTrue();
        assertThat(sent.get()).as("bytes the server managed to send").isLessThan(100L * 1024 * 1024);
    }
}
