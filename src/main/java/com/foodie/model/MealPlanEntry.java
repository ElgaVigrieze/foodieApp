package com.foodie.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.time.DayOfWeek;

/**
 * An entry in a meal plan — a meal assigned to a specific day and slot.
 */
@Entity
@Table(name = "meal_plan_entries")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class MealPlanEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "meal_plan_id", nullable = false)
    private MealPlan mealPlan;

    @NotNull
    @Enumerated(EnumType.STRING)
    private DayOfWeek dayOfWeek;

    @NotNull
    @Enumerated(EnumType.STRING)
    private MealSlot slot;

    /** Number of servings planned for this entry (default 1). */
    @Builder.Default
    private Integer servings = 1;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "meal_id", nullable = false)
    @NotNull
    private Meal meal;

    /** Whether this entry's meal has been prepared/cooked already. */
    @Builder.Default
    private boolean prepared = false;
}
