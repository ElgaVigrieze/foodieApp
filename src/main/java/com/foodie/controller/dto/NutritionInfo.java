package com.foodie.controller.dto;

import java.math.BigDecimal;

/**
 * Normalized nutrition info per 100g.
 */
public record NutritionInfo(
        BigDecimal calories,
        BigDecimal protein,
        BigDecimal carbs,
        BigDecimal fat,
        BigDecimal fiber,
        BigDecimal sugar
) {}
