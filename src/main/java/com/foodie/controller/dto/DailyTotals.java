package com.foodie.controller.dto;

import java.math.BigDecimal;

/**
 * Daily nutrition totals.
 */
public record DailyTotals(
        BigDecimal calories,
        BigDecimal protein,
        BigDecimal carbs,
        BigDecimal fat,
        BigDecimal fiber,
        BigDecimal sugar,
        BigDecimal netCarbs
) {}
