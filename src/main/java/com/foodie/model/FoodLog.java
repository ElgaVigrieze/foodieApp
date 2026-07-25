package com.foodie.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * A food log entry — records a serving of a meal consumed on a date.
 */
@Entity
@Table(name = "food_logs")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class FoodLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    private LocalDate date;

    /** Owner of this log entry (nullable for legacy data). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private AppUser owner;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "meal_id", nullable = false)
    @NotNull
    private Meal meal;

    /** Number of servings consumed (can be fractional, e.g. 0.5). */
    @NotNull
    @Positive
    private BigDecimal servingsConsumed;

    // ── Computed macros for this log entry ──────────────────────────────────

    @Transient
    public BigDecimal getCalories() {
        return meal.getCaloriesPerServing().multiply(servingsConsumed);
    }

    @Transient
    public BigDecimal getProtein() {
        return meal.getProteinPerServing().multiply(servingsConsumed);
    }

    @Transient
    public BigDecimal getCarbs() {
        return meal.getCarbsPerServing().multiply(servingsConsumed);
    }

    @Transient
    public BigDecimal getFat() {
        return meal.getFatPerServing().multiply(servingsConsumed);
    }

    @Transient
    public BigDecimal getFiber() {
        return meal.getFiberPerServing().multiply(servingsConsumed);
    }

    @Transient
    public BigDecimal getSugar() {
        return meal.getSugarPerServing().multiply(servingsConsumed);
    }

    @Transient
    public BigDecimal getNetCarbs() {
        return meal.getNetCarbsPerServing().multiply(servingsConsumed);
    }
}
