package com.foodie.controller.form;

import com.foodie.model.ProductCategory;
import com.foodie.model.Unit;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Create/edit product form. Deliberately does not contain {@code household}: binding the JPA
 * entity directly would let a user post {@code household.id=...} (mass assignment).
 */
public record ProductForm(
        Long id,
        @NotBlank @Size(max = 255) String name,
        ProductCategory category,
        @NotNull Unit unit,
        @NotNull @PositiveOrZero BigDecimal pricePerUnit,
        @PositiveOrZero BigDecimal calories,
        @PositiveOrZero BigDecimal protein,
        @PositiveOrZero BigDecimal carbs,
        @PositiveOrZero BigDecimal fat,
        @PositiveOrZero BigDecimal fiber,
        @PositiveOrZero BigDecimal sugar,
        @Size(max = 64) String barcode
) {}
