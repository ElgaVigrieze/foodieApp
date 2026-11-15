package com.foodie.client;

import com.foodie.exception.UnsafeUrlException;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.DnsResolver;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.DefaultRedirectStrategy;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.util.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Fetches web pages on behalf of a user while defending against SSRF.
 *
 * <p>Checking a hostname and then letting the HTTP client look it up again leaves a gap: DNS can
 * answer "public IP" for the check and "10.0.0.1" for the real connection (DNS rebinding), and a
 * redirect can point somewhere internal. This class closes both by installing a custom
 * {@link DnsResolver} in the HTTP client. Every lookup the client performs, for the original URL
 * and for each redirect hop, goes through {@link #resolvePublic}, which
 * <ul>
 *   <li>fails closed when the name cannot be resolved,</li>
 *   <li>rejects the request if <em>any</em> returned address is not public, and</li>
 *   <li>returns the validated addresses, which are the only ones the client may connect to
 *       (TLS still uses the hostname for SNI and certificate checks).</li>
 * </ul>
 * Only http/https on the standard ports are accepted, redirects are capped, and the response body
 * is size-limited.
 */
@Slf4j
@Component
public class SafeHttpFetcher {

    static final int MAX_REDIRECTS = 5;
    static final int MAX_BODY_BYTES = 2 * 1024 * 1024;
    private static final Set<Integer> STANDARD_PORTS = Set.of(80, 443);
    private static final Set<String> BLOCKED_HOSTS = Set.of("localhost", "metadata.google.internal");
    private static final Set<String> BLOCKED_SUFFIXES =
            Set.of(".internal", ".local", ".localhost", ".lan", ".home.arpa");

    private final CloseableHttpClient client;
    private final Predicate<InetAddress> addressAllowed;
    private final Predicate<Integer> portAllowed;

    @Autowired
    public SafeHttpFetcher(
            @Value("${app.cloudflare.connect-timeout-ms:15000}") int connectTimeoutMs,
            @Value("${app.cloudflare.read-timeout-ms:60000}") int readTimeoutMs) {
        this(connectTimeoutMs, readTimeoutMs, SafeHttpFetcher::isPublicAddress, STANDARD_PORTS::contains);
    }

    /** Lets tests relax the address and port rules to talk to a local server. Not used in production. */
    SafeHttpFetcher(int connectTimeoutMs, int readTimeoutMs,
                    Predicate<InetAddress> addressAllowed, Predicate<Integer> portAllowed) {
        this.addressAllowed = addressAllowed;
        this.portAllowed = portAllowed;

        PoolingHttpClientConnectionManager connectionManager = PoolingHttpClientConnectionManagerBuilder.create()
                .setDnsResolver(new ValidatingDnsResolver())
                .setDefaultConnectionConfig(ConnectionConfig.custom()
                        .setConnectTimeout(Timeout.ofMilliseconds(connectTimeoutMs))
                        .setSocketTimeout(Timeout.ofMilliseconds(readTimeoutMs))
                        .build())
                .build();

        this.client = HttpClients.custom()
                .setConnectionManager(connectionManager)
                .setDefaultRequestConfig(RequestConfig.custom()
                        .setMaxRedirects(MAX_REDIRECTS)
                        .setResponseTimeout(Timeout.ofMilliseconds(readTimeoutMs))
                        .build())
                // Every redirect target gets the same scheme/port/credential/hostname checks as the
                // first URL (the DNS resolver then checks the addresses when the client connects).
                // Request interceptors are not used for this: they do not run for redirect hops.
                .setRedirectStrategy(new DefaultRedirectStrategy() {
                    @Override
                    public URI getLocationURI(HttpRequest request, HttpResponse response, HttpContext context)
                            throws HttpException {
                        URI target = super.getLocationURI(request, response, context);
                        validate(target.toString());
                        return target;
                    }
                })
                .disableCookieManagement()
                .disableAuthCaching()
                .build();
    }

    // ── Public API ─────────────────────────────────────────────────────────

    /**
     * Rejects URLs that are malformed, use a disallowed scheme or port, or name an obviously
     * internal host. Cheap and offline; the DNS-based checks happen again at connection time.
     *
     * @throws UnsafeUrlException if the URL must not be fetched
     */
    public void validate(String url) {
        if (url == null || url.isBlank()) {
            throw new UnsafeUrlException("URL cannot be empty");
        }
        URI uri;
        try {
            uri = new URI(url.trim());
        } catch (Exception e) {
            throw new UnsafeUrlException("Invalid URL format");
        }
        String scheme = uri.getScheme();
        if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
            throw new UnsafeUrlException("Only HTTP and HTTPS URLs are allowed");
        }
        if (uri.getRawUserInfo() != null) {
            throw new UnsafeUrlException("URLs with embedded credentials are not allowed");
        }
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new UnsafeUrlException("URL must have a valid host");
        }
        int port = uri.getPort();
        if (port != -1 && !portAllowed.test(port)) {
            throw new UnsafeUrlException("Port " + port + " is not allowed");
        }
        rejectInternalHostname(host);
    }

    /**
     * Downloads a page as text. Validates the URL first, then fetches with DNS-time protection.
     *
     * @return the body decoded as text (at most {@value #MAX_BODY_BYTES} bytes), or {@code null} if empty
     * @throws UnsafeUrlException if the URL, or any redirect it leads to, is not allowed
     * @throws IOException        on network failures or HTTP error statuses
     */
    public String fetchText(String url, Map<String, String> headers) throws IOException {
        validate(url);
        HttpGet request = new HttpGet(url.trim());
        headers.forEach(request::addHeader);
        // executeOpen, not execute(request, handler): the latter drains the whole body after the
        // handler returns, which would defeat the size cap.
        ClassicHttpResponse response = null;
        try {
            response = client.executeOpen(null, request, null);
            int status = response.getCode();
            if (status >= 400) {
                throw new IOException("HTTP " + status);
            }
            HttpEntity entity = response.getEntity();
            return entity == null ? null : readLimited(entity, request);
        } catch (RuntimeException | IOException e) {
            UnsafeUrlException unsafe = findUnsafe(e);
            if (unsafe != null) throw unsafe;
            throw e;
        } finally {
            if (response != null) {
                try {
                    response.close();
                } catch (IOException | RuntimeException ignored) {
                    // the connection was aborted or is already closed
                }
            }
        }
    }

    // ── Address rules ──────────────────────────────────────────────────────

    /**
     * Resolves a hostname and returns its addresses only if every one of them is allowed.
     *
     * @throws UnsafeUrlException   if any address is not allowed
     * @throws UnknownHostException if the name does not resolve (the request fails closed)
     */
    InetAddress[] resolvePublic(String host) throws UnknownHostException {
        rejectInternalHostname(host);
        InetAddress[] addresses = InetAddress.getAllByName(host);
        if (addresses.length == 0) {
            throw new UnknownHostException(host);
        }
        for (InetAddress address : addresses) {
            if (!addressAllowed.test(address)) {
                throw new UnsafeUrlException("Access to " + host + " is not allowed");
            }
        }
        return addresses;
    }

    private static void rejectInternalHostname(String host) {
        String h = host.toLowerCase(Locale.ROOT);
        if (h.endsWith(".")) h = h.substring(0, h.length() - 1);
        final String name = h;
        if (BLOCKED_HOSTS.contains(name) || name.startsWith("metadata.")
                || BLOCKED_SUFFIXES.stream().anyMatch(name::endsWith)) {
            throw new UnsafeUrlException("Access to " + host + " is not allowed");
        }
    }

    /**
     * True only for globally routable unicast addresses. Rejects loopback, wildcard, link-local,
     * private (RFC 1918 and IPv6 ULA), carrier-grade NAT, multicast, documentation, benchmarking,
     * reserved and NAT64 ranges. IPv4-mapped IPv6 addresses are unwrapped by the JDK to their IPv4
     * form, so {@code ::ffff:10.0.0.1} is judged as {@code 10.0.0.1}.
     */
    static boolean isPublicAddress(InetAddress address) {
        if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
                || address.isSiteLocalAddress() || address.isMulticastAddress()) {
            return false;
        }
        byte[] a = address.getAddress();
        if (a.length == 4) {
            int b0 = a[0] & 0xFF;
            int b1 = a[1] & 0xFF;
            int b2 = a[2] & 0xFF;
            if (b0 == 0) return false;                                       // 0.0.0.0/8 "this network"
            if (b0 == 100 && b1 >= 64 && b1 <= 127) return false;            // 100.64.0.0/10 carrier-grade NAT
            if (b0 == 192 && b1 == 0 && (b2 == 0 || b2 == 2)) return false;  // 192.0.0.0/24, 192.0.2.0/24
            if (b0 == 198 && (b1 == 18 || b1 == 19)) return false;           // 198.18.0.0/15 benchmarking
            if (b0 == 198 && b1 == 51 && b2 == 100) return false;            // 198.51.100.0/24 documentation
            if (b0 == 203 && b1 == 0 && b2 == 113) return false;             // 203.0.113.0/24 documentation
            return b0 < 240;                                                 // 240.0.0.0/4 reserved + broadcast
        }
        int b0 = a[0] & 0xFF;
        int b1 = a[1] & 0xFF;
        if (isIpv4Compatible(a)) return false;                                // ::a.b.c.d (deprecated)
        if (b0 == 0x20 && b1 == 0x02) return false;                           // 2002::/16 6to4 (embeds IPv4)
        if (b0 == 0x20 && b1 == 0x01 && a[2] == 0 && a[3] == 0) return false; // 2001::/32 Teredo
        if (b0 == 0x01 && b1 == 0 && a[2] == 0 && a[3] == 0
                && a[4] == 0 && a[5] == 0 && a[6] == 0 && a[7] == 0) {
            return false;                                                     // 100::/64 discard-only
        }
        if ((b0 & 0xFE) == 0xFC) return false;                               // fc00::/7 unique local
        if (b0 == 0xFE && (b1 & 0xC0) == 0xC0) return false;                // fec0::/10 deprecated site-local
        if (b0 == 0x20 && b1 == 0x01 && a[2] == 0x0D && (a[3] & 0xFF) == 0xB8) return false; // 2001:db8::/32
        if (b0 == 0x00 && b1 == 0x64 && (a[2] & 0xFF) == 0xFF && (a[3] & 0xFF) == 0x9B) return false; // 64:ff9b::/96
        return true;
    }

    // ── Internals ──────────────────────────────────────────────────────────

    /** True for ::a.b.c.d, where the first 96 bits are zero. */
    private static boolean isIpv4Compatible(byte[] a) {
        for (int i = 0; i < 12; i++) {
            if (a[i] != 0) return false;
        }
        return true;
    }

    /** Plugged into the HTTP client so that no connection can bypass {@link #resolvePublic}. */
    private final class ValidatingDnsResolver implements DnsResolver {
        @Override
        public InetAddress[] resolve(String host) throws UnknownHostException {
            return resolvePublic(host);
        }

        @Override
        public String resolveCanonicalHostname(String host) throws UnknownHostException {
            return resolvePublic(host)[0].getCanonicalHostName();
        }
    }

    /**
     * Reads at most {@link #MAX_BODY_BYTES}. If the server has more to send, the request is aborted,
     * which drops the connection: closing a chunked or length-delimited stream normally reads the
     * rest of the body so the connection can be reused, and that is exactly what must not happen.
     */
    private static String readLimited(HttpEntity entity, HttpGet request) throws IOException {
        Charset charset = StandardCharsets.UTF_8;
        ContentType contentType = ContentType.parseLenient(entity.getContentType());
        if (contentType != null && contentType.getCharset() != null) {
            charset = contentType.getCharset();
        }
        InputStream in = entity.getContent();
        byte[] body = in.readNBytes(MAX_BODY_BYTES + 1);
        if (body.length > MAX_BODY_BYTES) {
            request.abort();
            body = java.util.Arrays.copyOf(body, MAX_BODY_BYTES);
        } else {
            in.close();
        }
        return body.length == 0 ? null : new String(body, charset);
    }

    private static UnsafeUrlException findUnsafe(Throwable t) {
        for (Throwable c = t; c != null; c = c.getCause()) {
            if (c instanceof UnsafeUrlException u) return u;
            if (c.getCause() == c) break;
        }
        return null;
    }
}
