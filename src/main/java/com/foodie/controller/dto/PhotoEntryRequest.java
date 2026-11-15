package com.foodie.controller.dto;

import com.foodie.model.MealSlot;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Request for adding a photo-analyzed food entry.
 */
public record PhotoEntryRequest(
        @NotNull LocalDate date,
        @Size(max = 500) String description,
        @PositiveOrZero BigDecimal calories,
        @PositiveOrZero BigDecimal protein,
        @PositiveOrZero BigDecimal carbs,
        @PositiveOrZero BigDecimal fat,
        @PositiveOrZero BigDecimal fiber,
        MealSlot slot
) {}
