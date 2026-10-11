package com.foodie.controller.dto;

import com.foodie.model.MealSlot;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Request for submitting a voice-parsed food log entry.
 */
public record VoiceSubmitRequest(
        String type,
        LocalDate date,
        MealSlot slot,
        Long mealId,
        BigDecimal servings,
        Long productId,
        BigDecimal quantity
) {}
