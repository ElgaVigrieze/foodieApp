package com.foodie.service;

import com.foodie.exception.BadRequestException;
import com.foodie.model.BrainTestType;
import com.foodie.model.db.AppUser;
import com.foodie.model.db.BrainTestResult;
import lombok.experimental.UtilityClass;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Validates and builds {@link BrainTestResult}s. Shared by the token-authenticated
 * recorder ingestion and the session-authenticated manual entry so both apply the same rules.
 */
@UtilityClass
final class BrainResultFactory {

    private static final int MAX_SCORE_MS = 60_000;
    private static final LocalDate MIN_DATE = LocalDate.of(2000, 1, 1);

    static BrainTestResult build(AppUser owner,
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
            throw new BadRequestException("Please choose a test type");
        }

        String normalizedClientKey = blankToNull(clientKey);
        if (normalizedClientKey != null && normalizedClientKey.length() > 64) {
            throw new BadRequestException("clientKey must be at most 64 characters");
        }

        String normalizedSource = source == null ? "" : source.trim().toLowerCase(Locale.ROOT);
        if (!normalizedSource.matches("[a-z0-9_-]{1,32}")) {
            normalizedSource = "manual";
        }

        if (date != null && (date.isBefore(MIN_DATE) || date.isAfter(LocalDate.now().plusDays(2)))) {
            throw new BadRequestException("Date is out of range");
        }
        if (levels != null && (levels < 1 || levels > 10)) {
            throw new BadRequestException("Levels must be between 1 and 10");
        }
        if (roundsPerLevel != null && (roundsPerLevel < 1 || roundsPerLevel > 100)) {
            throw new BadRequestException("Rounds per level must be between 1 and 100");
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
                throw new BadRequestException("Accuracy must be between 0 and 100");
            }
            builder.processingAvgMs(processingAvgMs)
                    .processingAccuracy(processingAccuracy)
                    .levels(levels != null ? levels : 3)
                    .roundsPerLevel(roundsPerLevel != null ? roundsPerLevel : 10);
        }

        return builder.build();
    }

    static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static void validateScore(Integer value, String label) {
        if (value == null || value <= 0 || value > MAX_SCORE_MS) {
            throw new BadRequestException(label + " must be between 1 and " + MAX_SCORE_MS + " ms");
        }
    }
}
