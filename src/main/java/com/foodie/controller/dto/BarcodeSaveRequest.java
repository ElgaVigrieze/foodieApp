package com.foodie.controller.dto;

import java.math.BigDecimal;

/**
 * Request for saving a barcode product.
 */
public record BarcodeSaveRequest(
        String barcode,
        String name,
        BigDecimal calories,
        BigDecimal protein,
        BigDecimal carbs,
        BigDecimal fat,
        BigDecimal fiber,
        BigDecimal sugar
) {}
