package com.foodie.controller.dto;

import com.foodie.model.db.BrainTestResult;

import java.util.List;

/**
 * Everything the Brain page needs for a single month.
 */
public record BrainMonthSummary(
        List<BrainTestResult> results,
        long dayCount,
        long testCount,
        Double avgReactionMs,
        Integer bestReactionMs,
        Double avgProcessingMs,
        Double avgAccuracy,
        BrainTestResult latestReaction,
        BrainTestResult latestProcessing
) {
}
