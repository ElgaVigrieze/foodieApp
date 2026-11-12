package com.foodie.controller.dto;

import com.foodie.model.BrainTestType;
import com.foodie.model.db.BrainTestResult;

import java.time.Instant;
import java.time.LocalDate;

public record BrainIngestResponse(
        Long id,
        BrainTestType testType,
        LocalDate testDate,
        Instant recordedAt,
        Integer reactionMs,
        Integer bestReactionMs,
        Integer processingAvgMs,
        Integer processingAccuracy,
        boolean duplicate
) {

    public static BrainIngestResponse from(BrainTestResult result, boolean duplicate) {
        return new BrainIngestResponse(
                result.getId(),
                result.getTestType(),
                result.getTestDate(),
                result.getRecordedAt(),
                result.getReactionMs(),
                result.getBestReactionMs(),
                result.getProcessingAvgMs(),
                result.getProcessingAccuracy(),
                duplicate
        );
    }
}
