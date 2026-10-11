package com.foodie.controller.dto;

import java.math.BigDecimal;

/**
 * Result of a barcode lookup from nutrition API.
 */
public record NutritionBarcodeResult(
        String name,
        String quantity,
        BigDecimal calories,
        BigDecimal protein,
        BigDecimal carbs,
        BigDecimal fat,
        BigDecimal fiber,
        BigDecimal sugar
) {}
