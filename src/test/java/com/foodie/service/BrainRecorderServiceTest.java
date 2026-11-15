package com.foodie.service;

import com.foodie.controller.dto.BrainIngestRequest;
import com.foodie.controller.dto.BrainIngestResponse;
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
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BrainRecorderServiceTest {

    @Mock
    private BrainTestResultRepository brainTestResultRepository;

    @Mock
    private AppUserRepository appUserRepository;

    @InjectMocks
    private BrainRecorderService brainRecorderService;

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

        BrainIngestResponse response = brainRecorderService.ingest(request, "token-1");

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

        BrainIngestResponse response = brainRecorderService.ingest(
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

        assertThatThrownBy(() -> brainRecorderService.ingest(
                new BrainIngestRequest(BrainTestType.REACTION_TIME, null, 250, null, null,
                        null, null, null, null, null, "userscript"),
                "bad"
        )).isInstanceOf(InvalidBrainTokenException.class);
    }

    @Test
    void ingest_rejectsMissingReactionScore() {
        when(appUserRepository.findByBrainToken("token-1")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> brainRecorderService.ingest(
                new BrainIngestRequest(BrainTestType.REACTION_TIME, null, null, null, null,
                        null, null, null, null, null, "userscript"),
                "token-1"
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Reaction time");
    }

    @Test
    void ingest_rejectsInvalidProcessingAccuracy() {
        when(appUserRepository.findByBrainToken("token-1")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> brainRecorderService.ingest(
                new BrainIngestRequest(BrainTestType.PROCESSING_SPEED, null, null, null, null,
                        430, 120, 3, 10, null, "userscript"),
                "token-1"
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Accuracy");
    }

    @Test
    void ingest_rejectsOutOfRangeDate() {
        when(appUserRepository.findByBrainToken("token-1")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> brainRecorderService.ingest(
                new BrainIngestRequest(BrainTestType.REACTION_TIME, LocalDate.now().plusYears(5), 250,
                        null, null, null, null, null, null, null, "userscript"),
                "token-1"
        )).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Date");
    }

    @Test
    void ingest_rejectsOutOfRangeLevels() {
        when(appUserRepository.findByBrainToken("token-1")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> brainRecorderService.ingest(
                new BrainIngestRequest(BrainTestType.PROCESSING_SPEED, null, null, null, null,
                        430, 95, 9999, 10, null, "userscript"),
                "token-1"
        )).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Levels");
    }

    @Test
    void ingest_unknownSourceFallsBackToManual() {
        when(appUserRepository.findByBrainToken("token-1")).thenReturn(Optional.of(user));
        when(brainTestResultRepository.save(any(BrainTestResult.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        brainRecorderService.ingest(new BrainIngestRequest(BrainTestType.REACTION_TIME, null, 250, null, null,
                null, null, null, null, null, "<script>evil</script>"), "token-1");

        verify(brainTestResultRepository).save(argThat(r -> "manual".equals(r.getSource())));
    }

    @Test
    void findDuplicate_returnsStoredResultForClientKey() {
        BrainTestResult existing = BrainTestResult.builder()
                .id(7L).owner(user).testType(BrainTestType.REACTION_TIME)
                .testDate(LocalDate.of(2026, 11, 7)).recordedAt(Instant.now()).reactionMs(250).build();
        when(appUserRepository.findByBrainToken("token-1")).thenReturn(Optional.of(user));
        when(brainTestResultRepository.findByOwnerIdAndClientKey(1L, "k")).thenReturn(Optional.of(existing));

        Optional<BrainIngestResponse> found = brainRecorderService.findDuplicate(
                new BrainIngestRequest(BrainTestType.REACTION_TIME, null, 250, null, null,
                        null, null, null, null, "k", "userscript"), "token-1");

        assertThat(found).isPresent();
        assertThat(found.get().duplicate()).isTrue();
        assertThat(found.get().id()).isEqualTo(7L);
    }

    @Test
    void touchRecorder_marksRecorderAsSeen() {
        when(appUserRepository.findByBrainToken("token-1")).thenReturn(Optional.of(user));
        when(appUserRepository.save(user)).thenReturn(user);

        AppUser saved = brainRecorderService.touchRecorder("token-1");

        assertThat(saved).isSameAs(user);
        assertThat(user.getBrainLastSeenAt()).isNotNull();
        verify(appUserRepository).save(user);
    }

    @Test
    void touchRecorder_rejectsUnknownToken() {
        when(appUserRepository.findByBrainToken("bad")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> brainRecorderService.touchRecorder("bad"))
                .isInstanceOf(InvalidBrainTokenException.class);
    }
}
