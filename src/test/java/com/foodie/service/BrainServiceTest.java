package com.foodie.service;

import com.foodie.controller.dto.BrainIngestRequest;
import com.foodie.controller.dto.BrainIngestResponse;
import com.foodie.controller.dto.BrainMonthSummary;
import com.foodie.model.BrainTestType;
import com.foodie.model.db.AppUser;
import com.foodie.model.db.BrainTestResult;
import com.foodie.repository.AppUserRepository;
import com.foodie.repository.BrainTestResultRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BrainServiceTest {

    @Mock
    private BrainTestResultRepository brainTestResultRepository;

    @Mock
    private AppUserRepository appUserRepository;

    @Mock
    private CurrentUserService currentUserService;

    @InjectMocks
    private BrainService brainService;

    private AppUser user;

    @BeforeEach
    void setUp() {
        user = AppUser.builder()
                .id(1L)
                .googleId("google-1")
                .email("test@example.com")
                .brainToken("token-1")
                .build();
    }

    @Test
    void ingest_savesReactionResultWithBestAttempt() {
        when(appUserRepository.findByBrainToken("token-1")).thenReturn(Optional.of(user));
        when(brainTestResultRepository.findByOwnerIdAndClientKey(1L, "client-1")).thenReturn(Optional.empty());
        when(brainTestResultRepository.save(any(BrainTestResult.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        BrainIngestRequest request = new BrainIngestRequest(
                BrainTestType.REACTION_TIME,
                LocalDate.of(2026, 11, 7),
                250,
                400, // deliberately worse than the best round; must be replaced
                List.of(250, 220, 300),
                null,
                null,
                null,
                null,
                "client-1",
                "userscript"
        );

        BrainIngestResponse response = brainService.ingest(request, "token-1");

        assertThat(response.duplicate()).isFalse();
        assertThat(response.testType()).isEqualTo(BrainTestType.REACTION_TIME);
        assertThat(response.testDate()).isEqualTo(LocalDate.of(2026, 11, 7));
        assertThat(response.reactionMs()).isEqualTo(250);
        assertThat(response.bestReactionMs()).isEqualTo(220);
        verify(brainTestResultRepository).save(argThat(result ->
                result.getAttemptsCount() == 3
                        && "250,220,300".equals(result.getAttemptsCsv())
                        && result.getOwner() == user
                        && "userscript".equals(result.getSource())
                        && result.getRecordedAt() != null
        ));
        verify(appUserRepository).save(user);
        assertThat(user.getBrainLastSyncAt()).isNotNull();
    }

    @Test
    void ingest_duplicateClientKey_returnsExistingResult() {
        BrainTestResult existing = BrainTestResult.builder()
                .id(99L)
                .owner(user)
                .testType(BrainTestType.REACTION_TIME)
                .testDate(LocalDate.of(2026, 11, 7))
                .recordedAt(Instant.parse("2026-11-07T08:00:00Z"))
                .reactionMs(250)
                .build();

        when(appUserRepository.findByBrainToken("token-1")).thenReturn(Optional.of(user));
        when(brainTestResultRepository.findByOwnerIdAndClientKey(1L, "client-1")).thenReturn(Optional.of(existing));

        BrainIngestResponse response = brainService.ingest(
                new BrainIngestRequest(BrainTestType.REACTION_TIME, null, 250, null, null,
                        null, null, null, null, "client-1", "userscript"),
                "token-1"
        );

        assertThat(response.duplicate()).isTrue();
        assertThat(response.id()).isEqualTo(99L);
        verify(brainTestResultRepository, never()).save(any());
        verify(appUserRepository, never()).save(any());
    }

    @Test
    void ingest_rejectsUnknownToken() {
        when(appUserRepository.findByBrainToken("bad")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> brainService.ingest(
                new BrainIngestRequest(BrainTestType.REACTION_TIME, null, 250, null, null,
                        null, null, null, null, null, "userscript"),
                "bad"
        )).isInstanceOf(InvalidBrainTokenException.class);
    }

    @Test
    void ingest_rejectsMissingReactionScore() {
        when(appUserRepository.findByBrainToken("token-1")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> brainService.ingest(
                new BrainIngestRequest(BrainTestType.REACTION_TIME, null, null, null, null,
                        null, null, null, null, null, "userscript"),
                "token-1"
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Reaction time");
    }

    @Test
    void ingest_rejectsInvalidProcessingAccuracy() {
        when(appUserRepository.findByBrainToken("token-1")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> brainService.ingest(
                new BrainIngestRequest(BrainTestType.PROCESSING_SPEED, null, null, null, null,
                        430, 120, 3, 10, null, "userscript"),
                "token-1"
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Accuracy");
    }

    @Test
    void getMonth_aggregatesReactionAndProcessingScores() {
        BrainTestResult reaction1 = BrainTestResult.builder()
                .owner(user)
                .testType(BrainTestType.REACTION_TIME)
                .testDate(LocalDate.of(2026, 11, 7))
                .recordedAt(Instant.parse("2026-11-07T08:00:00Z"))
                .reactionMs(250)
                .bestReactionMs(220)
                .attemptsCount(4)
                .build();

        BrainTestResult processing1 = BrainTestResult.builder()
                .owner(user)
                .testType(BrainTestType.PROCESSING_SPEED)
                .testDate(LocalDate.of(2026, 11, 7))
                .recordedAt(Instant.parse("2026-11-07T08:05:00Z"))
                .processingAvgMs(400)
                .processingAccuracy(95)
                .build();

        BrainTestResult reaction2 = BrainTestResult.builder()
                .owner(user)
                .testType(BrainTestType.REACTION_TIME)
                .testDate(LocalDate.of(2026, 11, 8))
                .recordedAt(Instant.parse("2026-11-08T08:00:00Z"))
                .reactionMs(300)
                .build();

        BrainTestResult processing2 = BrainTestResult.builder()
                .owner(user)
                .testType(BrainTestType.PROCESSING_SPEED)
                .testDate(LocalDate.of(2026, 11, 8))
                .recordedAt(Instant.parse("2026-11-08T08:05:00Z"))
                .processingAvgMs(500)
                .processingAccuracy(85)
                .build();

        when(currentUserService.getCurrentUser()).thenReturn(user);
        when(brainTestResultRepository.findByOwnerIdAndTestDateBetweenOrderByTestDateDescRecordedAtDesc(
                eq(1L), eq(LocalDate.of(2026, 11, 1)), eq(LocalDate.of(2026, 11, 30))))
                .thenReturn(List.of(reaction2, processing2, reaction1, processing1));
        when(brainTestResultRepository.findTopByOwnerIdAndTestTypeOrderByRecordedAtDesc(1L, BrainTestType.REACTION_TIME))
                .thenReturn(Optional.of(reaction2));
        when(brainTestResultRepository.findTopByOwnerIdAndTestTypeOrderByRecordedAtDesc(1L, BrainTestType.PROCESSING_SPEED))
                .thenReturn(Optional.of(processing2));

        BrainMonthSummary summary = brainService.getMonth(YearMonth.of(2026, 11));

        assertThat(summary.testCount()).isEqualTo(4);
        assertThat(summary.dayCount()).isEqualTo(2);
        assertThat(summary.avgReactionMs()).isEqualTo(275.0);
        assertThat(summary.bestReactionMs()).isEqualTo(220);
        assertThat(summary.avgProcessingMs()).isEqualTo(450.0);
        assertThat(summary.avgAccuracy()).isEqualTo(90.0);
        assertThat(summary.latestReaction()).isSameAs(reaction2);
        assertThat(summary.latestProcessing()).isSameAs(processing2);
        assertThat(summary.results()).hasSize(4);
    }

    @Test
    void getOrCreateToken_generatesTokenWhenMissing() {
        user.setBrainToken(null);
        when(currentUserService.getCurrentUser()).thenReturn(user);
        when(appUserRepository.save(user)).thenReturn(user);

        String token = brainService.getOrCreateToken();

        assertThat(token).isNotBlank().hasSize(43);
        assertThat(user.getBrainToken()).isEqualTo(token);
        assertThat(user.getBrainTokenCreatedAt()).isNotNull();
        verify(appUserRepository).save(user);
    }

    @Test
    void regenerateToken_replacesTokenAndResetsConnectionStatus() {
        user.setBrainLastSyncAt(Instant.parse("2026-11-07T08:00:00Z"));
        when(currentUserService.getCurrentUser()).thenReturn(user);
        when(appUserRepository.save(user)).thenReturn(user);

        String token = brainService.regenerateToken();

        assertThat(token).isNotBlank().isNotEqualTo("token-1");
        assertThat(user.getBrainToken()).isEqualTo(token);
        assertThat(user.getBrainTokenCreatedAt()).isNotNull();
        assertThat(user.getBrainLastSyncAt()).isNull();
        verify(appUserRepository).save(user);
    }

    @Test
    void addManual_savesReactionResultForCurrentUser() {
        when(currentUserService.getCurrentUser()).thenReturn(user);
        when(brainTestResultRepository.save(any(BrainTestResult.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        BrainTestResult result = brainService.addManual(
                LocalDate.of(2026, 11, 7), BrainTestType.REACTION_TIME, 245, null, null);

        assertThat(result.getOwner()).isSameAs(user);
        assertThat(result.getReactionMs()).isEqualTo(245);
        assertThat(result.getSource()).isEqualTo("manual");
        assertThat(result.getTestDate()).isEqualTo(LocalDate.of(2026, 11, 7));
        verify(brainTestResultRepository).save(result);
    }
}
