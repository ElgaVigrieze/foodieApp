package com.foodie.controller.dto;

import com.foodie.model.MealSlot;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Request for submitting a voice-parsed food log entry.
 */
public record VoiceSubmitRequest(
        @NotBlank String type,
        @NotNull LocalDate date,
        MealSlot slot,
        Long mealId,
        @Positive BigDecimal servings,
        Long productId,
        @Positive BigDecimal quantity
) {}
