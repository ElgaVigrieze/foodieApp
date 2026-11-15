package com.foodie.support;

import com.foodie.model.db.AppUser;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.DefaultCsrfToken;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * Helpers for MockMvc tests that need a logged-in browser session and valid CSRF tokens without
 * the spring-security-test dependency.
 */
public final class TestSessions {

    private static final String CSRF = "test-csrf-token";

    private TestSessions() {}

    /**
     * Spring Security's default handler expects the request token XOR-masked with random bytes
     * (BREACH protection): base64url(random || random XOR token). Send it as the {@code _csrf} param.
     */
    public static String maskedCsrf() {
        byte[] token = CSRF.getBytes(StandardCharsets.UTF_8);
        byte[] random = new byte[token.length];
        new SecureRandom().nextBytes(random);
        byte[] combined = new byte[token.length * 2];
        System.arraycopy(random, 0, combined, 0, token.length);
        for (int i = 0; i < token.length; i++) {
            combined[token.length + i] = (byte) (random[i] ^ token[i]);
        }
        return Base64.getUrlEncoder().encodeToString(combined);
    }

    /** A logged-in browser session (OAuth2 principal + a known CSRF token for form posts). */
    public static MockHttpSession sessionFor(AppUser user) {
        var principal = new DefaultOAuth2User(
                List.of(new SimpleGrantedAuthority("ROLE_USER")),
                Map.of("sub", user.getGoogleId(), "email", user.getEmail(), "name", user.getName()),
                "sub");
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));

        MockHttpSession session = new MockHttpSession();
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);
        CsrfToken token = new DefaultCsrfToken("X-CSRF-TOKEN", "_csrf", CSRF);
        session.setAttribute("org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository.CSRF_TOKEN", token);
        return session;
    }
}
