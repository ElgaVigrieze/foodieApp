package com.foodie.service;

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
        when(appUserRepository.claimBrainToken(eq(1L), anyString(), any(Instant.class)))
                .thenAnswer(invocation -> {
                    user.setBrainToken(invocation.getArgument(1));
                    return 1;
                });
        when(appUserRepository.findById(1L)).thenReturn(Optional.of(user));

        String token = brainService.getOrCreateToken();

        assertThat(token).isNotBlank().hasSize(43);
        assertThat(user.getBrainToken()).isEqualTo(token);
    }

    @Test
    void getOrCreateToken_losingARaceReturnsTheStoredToken() {
        user.setBrainToken(null);
        AppUser stored = AppUser.builder().id(1L).brainToken("winner-token").build();
        when(currentUserService.getCurrentUser()).thenReturn(user);
        when(appUserRepository.claimBrainToken(eq(1L), anyString(), any(Instant.class))).thenReturn(0);
        when(appUserRepository.findById(1L)).thenReturn(Optional.of(stored));

        assertThat(brainService.getOrCreateToken()).isEqualTo("winner-token");
    }

    @Test
    void getRecorderStatus_returnsTokenAndConnectionStateFromOneUser() {
        Instant seen = Instant.parse("2026-11-07T08:00:00Z");
        user.setBrainLastSeenAt(seen);
        when(currentUserService.getCurrentUser()).thenReturn(user);

        BrainService.RecorderStatus status = brainService.getRecorderStatus();

        assertThat(status.token()).isEqualTo("token-1");
        assertThat(status.lastSeenAt()).isEqualTo(seen);
        verify(currentUserService, times(1)).getCurrentUser();
    }

    @Test
    void delete_ownResultSucceeds() {
        BrainTestResult mine = BrainTestResult.builder().id(5L).owner(user).build();
        when(currentUserService.getCurrentUser()).thenReturn(user);
        when(brainTestResultRepository.findByIdAndOwnerId(5L, 1L)).thenReturn(Optional.of(mine));

        brainService.delete(5L);

        verify(brainTestResultRepository).delete(mine);
    }

    @Test
    void delete_someoneElsesResultLooksLikeMissing() {
        when(currentUserService.getCurrentUser()).thenReturn(user);
        when(brainTestResultRepository.findByIdAndOwnerId(99L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> brainService.delete(99L))
                .isInstanceOf(java.util.NoSuchElementException.class)
                .hasMessage("Result not found");
        verify(brainTestResultRepository, never()).delete(any());
    }

    @Test
    void addManual_savesProcessingSpeedResult() {
        when(currentUserService.getCurrentUser()).thenReturn(user);
        when(brainTestResultRepository.save(any(BrainTestResult.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        BrainTestResult result = brainService.addManual(
                LocalDate.of(2026, 11, 7), BrainTestType.PROCESSING_SPEED, null, 430, 96);

        assertThat(result.getProcessingAvgMs()).isEqualTo(430);
        assertThat(result.getProcessingAccuracy()).isEqualTo(96);
        assertThat(result.getLevels()).isEqualTo(3);
        assertThat(result.getRoundsPerLevel()).isEqualTo(10);
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

    @Test
    void regenerateToken_replacesTokenAndResetsConnectionStatus() {
        user.setBrainLastSyncAt(Instant.parse("2026-11-07T08:00:00Z"));
        user.setBrainLastSeenAt(Instant.parse("2026-11-07T08:00:00Z"));
        when(currentUserService.getCurrentUser()).thenReturn(user);
        when(appUserRepository.save(user)).thenReturn(user);

        String token = brainService.regenerateToken();

        assertThat(token).isNotBlank().isNotEqualTo("token-1");
        assertThat(user.getBrainToken()).isEqualTo(token);
        assertThat(user.getBrainTokenCreatedAt()).isNotNull();
        assertThat(user.getBrainLastSyncAt()).isNull();
        assertThat(user.getBrainLastSeenAt()).isNull();
        verify(appUserRepository).save(user);
    }
}
