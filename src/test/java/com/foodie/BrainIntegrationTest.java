package com.foodie;

import com.foodie.model.BrainTestType;
import com.foodie.model.db.AppUser;
import com.foodie.model.db.BrainTestResult;
import com.foodie.repository.AppUserRepository;
import com.foodie.repository.BrainTestResultRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:brain-test;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.security.oauth2.client.registration.google.client-id=test-client",
        "spring.security.oauth2.client.registration.google.client-secret=test-secret"
})
@AutoConfigureMockMvc
class BrainIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private BrainTestResultRepository brainTestResultRepository;

    private AppUser user;

    @BeforeEach
    void setUp() {
        brainTestResultRepository.deleteAll();
        appUserRepository.deleteAll();

        user = appUserRepository.save(AppUser.builder()
                .googleId("google-1")
                .email("brain@example.com")
                .name("Brain Tester")
                .brainToken("token-1")
                .build());
    }

    @Test
    void ingestEndpointSavesResultOnceAndUpdatesLastSync() throws Exception {
        String body = """
                {
                  "testType": "REACTION_TIME",
                  "date": "2026-11-07",
                  "reactionMs": 250,
                  "bestReactionMs": 400,
                  "attempts": [250, 220, 300],
                  "clientKey": "client-1",
                  "source": "userscript"
                }
                """;

        mockMvc.perform(post("/api/brain/ingest")
                        .header("X-Brain-Token", "token-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.duplicate").value(false))
                .andExpect(jsonPath("$.reactionMs").value(250))
                .andExpect(jsonPath("$.bestReactionMs").value(220));

        assertThat(brainTestResultRepository.count()).isEqualTo(1);
        assertThat(appUserRepository.findById(user.getId()).orElseThrow().getBrainLastSyncAt()).isNotNull();

        // Same client key: the retry must be idempotent.
        mockMvc.perform(post("/api/brain/ingest")
                        .header("X-Brain-Token", "token-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.duplicate").value(true));

        assertThat(brainTestResultRepository.count()).isEqualTo(1);
    }

    @Test
    void ingestEndpointRejectsInvalidToken() throws Exception {
        mockMvc.perform(post("/api/brain/ingest")
                        .header("X-Brain-Token", "wrong-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"testType":"REACTION_TIME","reactionMs":250}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void corsPreflightAllowsHumanBenchmarkOrigins() throws Exception {
        mockMvc.perform(options("/api/brain/ingest")
                        .header("Origin", "https://humanbenchmark.com")
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "content-type,x-brain-token"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "https://humanbenchmark.com"));
    }

    @Test
    void brainPageRendersForLoggedInUser() throws Exception {
        brainTestResultRepository.save(BrainTestResult.builder()
                .owner(user)
                .testType(BrainTestType.REACTION_TIME)
                .testDate(LocalDate.of(2026, 11, 7))
                .recordedAt(Instant.parse("2026-11-07T08:00:00Z"))
                .reactionMs(250)
                .bestReactionMs(220)
                .attemptsCount(4)
                .source("userscript")
                .build());

        brainTestResultRepository.save(BrainTestResult.builder()
                .owner(user)
                .testType(BrainTestType.PROCESSING_SPEED)
                .testDate(LocalDate.of(2026, 11, 7))
                .recordedAt(Instant.parse("2026-11-07T08:05:00Z"))
                .processingAvgMs(430)
                .processingAccuracy(95)
                .levels(3)
                .source("userscript")
                .build());

        OAuth2User oauthUser = new DefaultOAuth2User(
                List.of(new SimpleGrantedAuthority("ROLE_USER")),
                Map.of("sub", "google-1", "email", "brain@example.com", "name", "Brain Tester"),
                "sub"
        );

        var authenticationToken = new UsernamePasswordAuthenticationToken(
                oauthUser, null, oauthUser.getAuthorities());

        SecurityContext securityContext = SecurityContextHolder.createEmptyContext();
        securityContext.setAuthentication(authenticationToken);

        MockHttpSession session = new MockHttpSession();
        session.setAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,
                securityContext
        );

        mockMvc.perform(get("/brain").param("month", "2026-11").session(session))
                .andExpect(status().isOk())
                .andExpect(view().name("brain/index"))
                .andExpect(content().string(containsString("Automatic recorder")))
                .andExpect(content().string(containsString("Reaction Time")))
                .andExpect(content().string(containsString("Processing Speed")))
                .andExpect(content().string(containsString("best seen 220 ms")))
                .andExpect(content().string(containsString("95% accuracy")))
                .andExpect(content().string(containsString("Add a result manually")));
    }
}
