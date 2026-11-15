package com.foodie.controller.form;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Form objects for the shopping list. {@code weekStart} selects the list and is the redirect target. */
public final class ShoppingListForms {

    private ShoppingListForms() {}

    /** Identifies one item (toggle checked, remove). */
    public record ItemRef(
            @NotNull Long itemId,
            @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate weekStart
    ) {}

    public record ItemQuantity(
            @NotNull Long itemId,
            @NotNull @PositiveOrZero BigDecimal quantity,
            @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate weekStart
    ) {}

    public record ItemComment(
            @NotNull Long itemId,
            @Size(max = 500) String comment,
            @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate weekStart
    ) {}

    public record AddItem(
            @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate weekStart,
            @NotNull Long productId,
            @NotNull @Positive BigDecimal quantity
    ) {}
}
