package com.foodie.service;

import com.foodie.controller.dto.BrainIngestRequest;
import com.foodie.controller.dto.BrainIngestResponse;
import com.foodie.controller.dto.BrainMonthSummary;
import com.foodie.model.BrainTestType;
import com.foodie.model.db.AppUser;
import com.foodie.model.db.BrainTestResult;
import com.foodie.repository.AppUserRepository;
import com.foodie.repository.BrainTestResultRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class BrainService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int MAX_SCORE_MS = 60_000;

    private final BrainTestResultRepository brainTestResultRepository;
    private final AppUserRepository appUserRepository;
    private final CurrentUserService currentUserService;

    // ── Recorder token ───────────────────────────────────────────────────────

    /** Returns the current user's recorder token, creating one on first use. */
    @Transactional
    public String getOrCreateToken() {
        AppUser user = requireUser();
        if (user.getBrainToken() == null || user.getBrainToken().isBlank()) {
            user.setBrainToken(newToken());
            user.setBrainTokenCreatedAt(Instant.now());
            appUserRepository.save(user);
        }
        return user.getBrainToken();
    }

    @Transactional
    public String regenerateToken() {
        AppUser user = requireUser();
        user.setBrainToken(newToken());
        user.setBrainTokenCreatedAt(Instant.now());
        // The old token is now invalid, so the "Connected" badge must reset too.
        user.setBrainLastSyncAt(null);
        appUserRepository.save(user);
        return user.getBrainToken();
    }

    public Instant getLastSyncAt() {
        AppUser user = requireUser();
        return user.getBrainLastSyncAt();
    }

    @Transactional(readOnly = true)
    public AppUser authenticate(String token) {
        if (token == null || token.isBlank()) {
            throw new InvalidBrainTokenException();
        }
        return appUserRepository.findByBrainToken(token)
                .orElseThrow(InvalidBrainTokenException::new);
    }

    // ── Result ingestion ─────────────────────────────────────────────────────

    @Transactional
    public BrainIngestResponse ingest(BrainIngestRequest request, String token) {
        AppUser user = authenticate(token);

        if (request == null) {
            throw new IllegalArgumentException("Missing result body");
        }

        // The recorder retries on flaky connections; a client key keeps that idempotent.
        // Scope the lookup to this user so one user's keys can never expose another's result.
        String requestClientKey = blankToNull(request.clientKey());
        if (requestClientKey != null) {
            Optional<BrainTestResult> existing = brainTestResultRepository
                    .findByOwnerIdAndClientKey(user.getId(), requestClientKey);
            if (existing.isPresent()) {
                return BrainIngestResponse.from(existing.get(), true);
            }
        }

        BrainTestResult result = buildResult(
                user,
                request.testType(),
                request.date(),
                request.reactionMs(),
                request.bestReactionMs(),
                request.attempts(),
                request.processingAvgMs(),
                request.processingAccuracy(),
                request.levels(),
                request.roundsPerLevel(),
                requestClientKey,
                request.source()
        );

        BrainTestResult saved = brainTestResultRepository.save(result);

        user.setBrainLastSyncAt(Instant.now());
        appUserRepository.save(user);

        log.info("Brain result saved for user {}: {} {}", user.getId(), saved.getTestType(), saved.getTestDate());
        return BrainIngestResponse.from(saved, false);
    }

    @Transactional
    public BrainTestResult addManual(LocalDate date,
                                     BrainTestType testType,
                                     Integer reactionMs,
                                     Integer processingAvgMs,
                                     Integer processingAccuracy) {
        AppUser user = requireUser();
        BrainTestResult result = buildResult(
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

    private BrainTestResult buildResult(AppUser owner,
                                        BrainTestType testType,
                                        LocalDate date,
                                        Integer reactionMs,
                                        Integer bestReactionMs,
                                        List<Integer> attempts,
                                        Integer processingAvgMs,
                                        Integer processingAccuracy,
                                        Integer levels,
                                        Integer roundsPerLevel,
                                        String clientKey,
                                        String source) {
        if (testType == null) {
            throw new IllegalArgumentException("Please choose a test type");
        }

        String normalizedClientKey = blankToNull(clientKey);
        if (normalizedClientKey != null && normalizedClientKey.length() > 64) {
            throw new IllegalArgumentException("clientKey must be at most 64 characters");
        }

        String normalizedSource = source == null || source.isBlank() ? "manual" : source.trim();
        if (normalizedSource.length() > 32) {
            normalizedSource = normalizedSource.substring(0, 32);
        }

        BrainTestResult.BrainTestResultBuilder builder = BrainTestResult.builder()
                .owner(owner)
                .testType(testType)
                .testDate(date != null ? date : LocalDate.now())
                .recordedAt(Instant.now())
                .clientKey(normalizedClientKey)
                .source(normalizedSource);

        if (testType == BrainTestType.REACTION_TIME) {
            validateScore(reactionMs, "Reaction time");
            if (bestReactionMs != null) {
                validateScore(bestReactionMs, "Best reaction time");
            }

            List<Integer> cleanAttempts = attempts == null ? List.of() : attempts.stream()
                    .filter(Objects::nonNull)
                    .filter(value -> value > 0 && value <= MAX_SCORE_MS)
                    .limit(50)
                    .toList();

            Integer fastest = cleanAttempts.stream().min(Integer::compareTo).orElse(bestReactionMs);

            builder.reactionMs(reactionMs)
                    .bestReactionMs(fastest)
                    .attemptsCount(cleanAttempts.isEmpty() ? null : cleanAttempts.size());

            if (!cleanAttempts.isEmpty()) {
                builder.attemptsCsv(cleanAttempts.stream()
                        .map(String::valueOf)
                        .collect(Collectors.joining(",")));
            }
        } else {
            validateScore(processingAvgMs, "Processing speed");
            if (processingAccuracy == null || processingAccuracy < 0 || processingAccuracy > 100) {
                throw new IllegalArgumentException("Accuracy must be between 0 and 100");
            }
            builder.processingAvgMs(processingAvgMs)
                    .processingAccuracy(processingAccuracy)
                    .levels(levels != null ? levels : 3)
                    .roundsPerLevel(roundsPerLevel != null ? roundsPerLevel : 10);
        }

        return builder.build();
    }

    private void validateScore(Integer value, String label) {
        if (value == null || value <= 0 || value > MAX_SCORE_MS) {
            throw new IllegalArgumentException(label + " must be between 1 and " + MAX_SCORE_MS + " ms");
        }
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

    @Transactional
    public void delete(Long id) {
        AppUser user = requireUser();
        BrainTestResult result = brainTestResultRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Result not found: " + id));

        if (result.getOwner() == null || !result.getOwner().getId().equals(user.getId())) {
            throw new SecurityException("Not authorized to delete this result");
        }

        brainTestResultRepository.delete(result);
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

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String newToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
