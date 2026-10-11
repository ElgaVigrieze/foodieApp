package com.foodie.controller.dto;

import com.foodie.model.MealSlot;

import java.math.BigDecimal;

/**
 * Result of parsing voice input for food logging.
 */
public record VoiceParseResult(
        boolean parsed,
        String type,
        Long mealId,
        String mealName,
        Long productId,
        String productName,
        BigDecimal servings,
        BigDecimal quantity,
        MealSlot slot,
        String error
) {
    public static VoiceParseResult meal(Long mealId, String mealName, BigDecimal servings, MealSlot slot) {
        return new VoiceParseResult(true, "meal", mealId, mealName, null, null, servings, null, slot, null);
    }

    public static VoiceParseResult product(Long productId, String productName, BigDecimal quantity, MealSlot slot) {
        return new VoiceParseResult(true, "product", null, null, productId, productName, null, quantity, slot, null);
    }

    public static VoiceParseResult notFound(String message) {
        return new VoiceParseResult(false, null, null, null, null, null, null, null, null, message);
    }

    public static VoiceParseResult error(String message) {
        return new VoiceParseResult(false, null, null, null, null, null, null, null, null, message);
    }
}
