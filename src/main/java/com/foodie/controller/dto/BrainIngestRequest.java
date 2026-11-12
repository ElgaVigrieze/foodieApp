package com.foodie.controller.dto;

import com.foodie.model.BrainTestType;

import java.time.LocalDate;
import java.util.List;

/**
 * Result posted by the browser recorder (or the manual-entry form).
 * Only the fields relevant to {@link #testType()} are required.
 */
public record BrainIngestRequest(
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
        String source
) {
}
