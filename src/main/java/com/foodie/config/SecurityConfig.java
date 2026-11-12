package com.foodie.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final CustomOAuth2UserService customOAuth2UserService;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/login", "/error", "/css/**", "/js/**", "/health",
                                "/api/brain/ingest", "/api/brain/ping").permitAll()
                        .anyRequest().authenticated()
                )
                .oauth2Login(oauth -> oauth
                        .loginPage("/login")
                        .userInfoEndpoint(info -> info.oidcUserService(customOAuth2UserService))
                        .defaultSuccessUrl("/meals", true)
                )
                .logout(logout -> logout
                        .logoutSuccessUrl("/login")
                )
                // The browser recorder authenticates with its own token, not a session/CSRF token.
                .csrf(csrf -> csrf.ignoringRequestMatchers("/h2-console/**", "/api/brain/**"))
                .cors(Customizer.withDefaults())
                .headers(headers -> headers.frameOptions(f -> f.sameOrigin()));

        return http.build();
    }

    /**
     * Allows the browser recorder (running on the Human Benchmark pages) to call
     * the token-authenticated ingest endpoint. No session cookies are involved.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of(
                "https://humanbenchmark.com",
                "https://www.humanbenchmark.com",
                "https://humanbenchmark.now",
                "https://www.humanbenchmark.now"
        ));
        configuration.setAllowedMethods(List.of("GET", "POST", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Content-Type", "X-Brain-Token"));
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/brain/**", configuration);
        return source;
    }
}
