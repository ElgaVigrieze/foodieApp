package com.foodie.controller.dto;

import com.foodie.model.MealSlot;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Request for adding a photo-analyzed food entry.
 */
public record PhotoEntryRequest(
        LocalDate date,
        String description,
        BigDecimal calories,
        BigDecimal protein,
        BigDecimal carbs,
        BigDecimal fat,
        BigDecimal fiber,
        MealSlot slot
) {}
