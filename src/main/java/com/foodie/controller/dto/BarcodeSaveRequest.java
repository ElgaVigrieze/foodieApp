package com.foodie.controller.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Request for saving a barcode product.
 */
public record BarcodeSaveRequest(
        @Size(max = 64) String barcode,
        @NotBlank @Size(max = 255) String name,
        @PositiveOrZero BigDecimal calories,
        @PositiveOrZero BigDecimal protein,
        @PositiveOrZero BigDecimal carbs,
        @PositiveOrZero BigDecimal fat,
        @PositiveOrZero BigDecimal fiber,
        @PositiveOrZero BigDecimal sugar
) {}
