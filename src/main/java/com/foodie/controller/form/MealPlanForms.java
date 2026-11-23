package com.foodie.controller.form;

import com.foodie.model.MealSlot;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.DayOfWeek;
import java.time.LocalDate;

/** Form objects for the weekly meal plan. {@code weekStart} is only used to redirect back. */
public final class MealPlanForms {

    private MealPlanForms() {}

    public record AddEntry(
            @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate weekStart,
            @NotNull DayOfWeek day,
            @NotNull MealSlot slot,
            @NotNull Long mealId,
            @Positive Integer servings
    ) {
        public AddEntry {
            if (servings == null) servings = 1;
        }
    }

    /** Identifies one entry of a plan (remove, toggle prepared). */
    public record EntryRef(
            @NotNull Long planId,
            @NotNull Long entryId,
            @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate weekStart
    ) {}

    /** Move or copy an entry to another day/slot. */
    public record EntryTarget(
            @NotNull Long planId,
            @NotNull Long entryId,
            @NotNull DayOfWeek newDay,
            @NotNull MealSlot newSlot,
            @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate weekStart
    ) {}

    public record EntryServings(
            @NotNull Long planId,
            @NotNull Long entryId,
            @NotNull @Positive Integer servings,
            @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate weekStart
    ) {}

    /** Copy a whole week into the viewed week; {@code weekStart} is the target and the redirect target. */
    public record CopyWeek(
            @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate sourceWeek,
            @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate weekStart
    ) {}

    public record PlanRef(
            @NotNull Long planId,
            @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate weekStart
    ) {}
}
