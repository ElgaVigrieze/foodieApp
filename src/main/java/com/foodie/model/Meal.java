package com.foodie.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * A meal composed of products with quantities.
 */
@Entity
@Table(name = "meals")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class Meal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    private String name;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(columnDefinition = "VARCHAR(50)")
    private MealCategory category;

    /** Number of servings this meal yields. */
    @NotNull
    @Positive
    private Integer servings;

    /** Free-text recipe / preparation instructions. */
    @Column(columnDefinition = "TEXT")
    private String recipe;

    /** Whether this meal is marked as a favorite. */
    @Builder.Default
    private boolean favorite = false;

    /** Owner of this meal (nullable for legacy data). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private AppUser owner;

    @OneToMany(mappedBy = "meal", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @Builder.Default
    private List<MealIngredient> ingredients = new ArrayList<>();

    // ── Computed fields ────────────────────────────────────────────────────

    @Transient
    public BigDecimal getTotalCost() {
        return ingredients.stream()
                .map(MealIngredient::getCost)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @Transient
    public BigDecimal getCostPerServing() {
        if (servings == null || servings == 0) return BigDecimal.ZERO;
        return getTotalCost().divide(BigDecimal.valueOf(servings), 2, RoundingMode.HALF_UP);
    }

    @Transient
    public BigDecimal getCaloriesPerServing() {
        return getNutrientPerServing(NutrientType.CALORIES);
    }

    @Transient
    public BigDecimal getProteinPerServing() {
        return getNutrientPerServing(NutrientType.PROTEIN);
    }

    @Transient
    public BigDecimal getCarbsPerServing() {
        return getNutrientPerServing(NutrientType.CARBS);
    }

    @Transient
    public BigDecimal getFatPerServing() {
        return getNutrientPerServing(NutrientType.FAT);
    }

    @Transient
    public BigDecimal getFiberPerServing() {
        return getNutrientPerServing(NutrientType.FIBER);
    }

    @Transient
    public BigDecimal getSugarPerServing() {
        return getNutrientPerServing(NutrientType.SUGAR);
    }

    @Transient
    public BigDecimal getNetCarbsPerServing() {
        BigDecimal carbs = getCarbsPerServing();
        BigDecimal fiber = getFiberPerServing();
        if (carbs == null || fiber == null) return carbs;
        return carbs.subtract(fiber);
    }

    private BigDecimal getNutrientPerServing(NutrientType type) {
        if (servings == null || servings == 0) return BigDecimal.ZERO;
        BigDecimal total = ingredients.stream()
                .map(i -> i.getNutrientAmount(type))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return total.divide(BigDecimal.valueOf(servings), 1, RoundingMode.HALF_UP);
    }

    // ── Helper to manage bidirectional relationship ────────────────────────

    public void addIngredient(MealIngredient ingredient) {
        ingredients.add(ingredient);
        ingredient.setMeal(this);
    }

    public void removeIngredient(MealIngredient ingredient) {
        ingredients.remove(ingredient);
        ingredient.setMeal(null);
    }
}
