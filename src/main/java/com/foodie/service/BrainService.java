package com.foodie.service;

import com.foodie.controller.dto.BrainMonthSummary;
import com.foodie.model.BrainTestType;
import com.foodie.model.db.AppUser;
import com.foodie.model.db.BrainTestResult;
import com.foodie.repository.AppUserRepository;
import com.foodie.repository.BrainTestResultRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Base64;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;

/**
 * Serves the Brain web page for the logged-in (session) user. The token-authenticated
 * recorder endpoints live in {@link BrainRecorderService}.
 */
@Service
@RequiredArgsConstructor
public class BrainService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final BrainTestResultRepository brainTestResultRepository;
    private final AppUserRepository appUserRepository;
    private final CurrentUserService currentUserService;

    // ── Recorder token ───────────────────────────────────────────────────────

    /** Returns the current user's recorder token, creating one on first use. */
    @Transactional
    public String getOrCreateToken() {
        AppUser user = requireUser();
        if (user.getBrainToken() != null && !user.getBrainToken().isBlank()) {
            return user.getBrainToken();
        }
        // Conditional update: if two first-time page loads race, only one token is ever stored.
        appUserRepository.claimBrainToken(user.getId(), newToken(), Instant.now());
        return appUserRepository.findById(user.getId())
                .map(AppUser::getBrainToken)
                .orElseThrow(() -> new IllegalStateException("Not authenticated"));
    }

    /** Everything the Brain page needs about the recorder, from a single user lookup. */
    public record RecorderStatus(String token, Instant lastSyncAt, Instant lastSeenAt) {
    }

    @Transactional
    public RecorderStatus getRecorderStatus() {
        AppUser user = requireUser();
        String token = user.getBrainToken();
        if (token == null || token.isBlank()) {
            token = getOrCreateToken();
        }
        return new RecorderStatus(token, user.getBrainLastSyncAt(), user.getBrainLastSeenAt());
    }

    @Transactional
    public String regenerateToken() {
        AppUser user = requireUser();
        user.setBrainToken(newToken());
        user.setBrainTokenCreatedAt(Instant.now());
        // The old token is now invalid, so the recorder status must reset too.
        user.setBrainLastSyncAt(null);
        user.setBrainLastSeenAt(null);
        appUserRepository.save(user);
        return user.getBrainToken();
    }

    // ── Manual entry ─────────────────────────────────────────────────────────

    @Transactional
    public BrainTestResult addManual(LocalDate date,
                                     BrainTestType testType,
                                     Integer reactionMs,
                                     Integer processingAvgMs,
                                     Integer processingAccuracy) {
        AppUser user = requireUser();
        BrainTestResult result = BrainResultFactory.build(
                user,
                testType,
                date,
                reactionMs,
                null,
                null,
                processingAvgMs,
                processingAccuracy,
                testType == BrainTestType.PROCESSING_SPEED ? 3 : null,
                testType == BrainTestType.PROCESSING_SPEED ? 10 : null,
                null,
                "manual"
        );
        return brainTestResultRepository.save(result);
    }

    @Transactional
    public void delete(Long id) {
        AppUser user = requireUser();
        // Scoped to the owner so another user's id looks exactly like a missing one.
        BrainTestResult result = brainTestResultRepository.findByIdAndOwnerId(id, user.getId())
                .orElseThrow(() -> new NoSuchElementException("Result not found"));

        brainTestResultRepository.delete(result);
    }

    // ── Monthly scoreboard ───────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public BrainMonthSummary getMonth(YearMonth month) {
        AppUser user = requireUser();
        LocalDate from = month.atDay(1);
        LocalDate to = month.atEndOfMonth();

        List<BrainTestResult> results = brainTestResultRepository
                .findByOwnerIdAndTestDateBetweenOrderByTestDateDescRecordedAtDesc(user.getId(), from, to);

        List<Integer> reactionValues = results.stream()
                .filter(BrainTestResult::isReactionTime)
                .map(BrainTestResult::getReactionMs)
                .filter(Objects::nonNull)
                .toList();

        List<Integer> bestValues = results.stream()
                .filter(BrainTestResult::isReactionTime)
                .map(BrainTestResult::getBestReactionMs)
                .filter(Objects::nonNull)
                .toList();

        // If no individual rounds were captured, fall back to the monthly averages.
        Integer bestReaction = (bestValues.isEmpty() ? reactionValues : bestValues).stream()
                .min(Integer::compareTo)
                .orElse(null);

        List<Integer> processingValues = results.stream()
                .filter(BrainTestResult::isProcessingSpeed)
                .map(BrainTestResult::getProcessingAvgMs)
                .filter(Objects::nonNull)
                .toList();

        List<Integer> accuracyValues = results.stream()
                .filter(BrainTestResult::isProcessingSpeed)
                .map(BrainTestResult::getProcessingAccuracy)
                .filter(Objects::nonNull)
                .toList();

        long dayCount = results.stream()
                .map(BrainTestResult::getTestDate)
                .distinct()
                .count();

        BrainTestResult latestReaction = brainTestResultRepository
                .findTopByOwnerIdAndTestTypeOrderByRecordedAtDesc(user.getId(), BrainTestType.REACTION_TIME)
                .orElse(null);

        BrainTestResult latestProcessing = brainTestResultRepository
                .findTopByOwnerIdAndTestTypeOrderByRecordedAtDesc(user.getId(), BrainTestType.PROCESSING_SPEED)
                .orElse(null);

        return new BrainMonthSummary(
                results,
                dayCount,
                results.size(),
                average(reactionValues),
                bestReaction,
                average(processingValues),
                average(accuracyValues),
                latestReaction,
                latestProcessing
        );
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private AppUser requireUser() {
        AppUser user = currentUserService.getCurrentUser();
        if (user == null) {
            throw new IllegalStateException("Not authenticated");
        }
        return user;
    }

    private static Double average(List<Integer> values) {
        return values.isEmpty() ? null : values.stream().mapToInt(Integer::intValue).average().orElse(0);
    }

    private static String newToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
