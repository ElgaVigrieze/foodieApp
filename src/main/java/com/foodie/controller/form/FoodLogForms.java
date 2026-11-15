package com.foodie.controller.form;

import com.foodie.model.MealSlot;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import org.springframework.format.annotation.DateTimeFormat;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Form objects for the daily food log. {@code date} is both data and the redirect target. */
public final class FoodLogForms {

    private FoodLogForms() {}

    public record AddMeal(
            @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @NotNull Long mealId,
            @NotNull @Positive BigDecimal servingsConsumed,
            MealSlot slot
    ) {}

    public record AddProduct(
            @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @NotNull Long productId,
            @NotNull @Positive BigDecimal quantity,
            MealSlot slot
    ) {}

    public record CaloriesSpent(
            @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @NotNull @PositiveOrZero BigDecimal caloriesSpent
    ) {}

    public record UpdateQuantity(
            @NotNull @Positive BigDecimal quantity,
            @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {}

    public record UpdateServings(
            @NotNull @Positive BigDecimal servings,
            @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {}

    public record Targets(
            @PositiveOrZero BigDecimal targetCalories,
            @PositiveOrZero BigDecimal targetFiber,
            @PositiveOrZero BigDecimal targetCarbs,
            @PositiveOrZero BigDecimal targetFat,
            @PositiveOrZero BigDecimal targetProtein,
            @PositiveOrZero BigDecimal targetCost
    ) {}
}
