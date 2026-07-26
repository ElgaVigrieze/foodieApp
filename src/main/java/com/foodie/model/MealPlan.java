package com.foodie.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * A weekly meal plan.
 */
@Entity
@Table(name = "meal_plans")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class MealPlan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    /** The Monday of this meal plan week. */
    @NotNull
    private LocalDate weekStart;

    /** Owner of this meal plan (nullable for legacy data). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private AppUser owner;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "household_id")
    private Household household;

    @OneToMany(mappedBy = "mealPlan", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @Builder.Default
    private List<MealPlanEntry> entries = new ArrayList<>();

    // â”€â”€ Computed: Shopping List â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    /**
     * Aggregates all ingredients across all meals in the plan.
     * Returns a map of Product â†’ total quantity needed.
     */
    @Transient
    public Map<Product, BigDecimal> getShoppingList() {
        return entries.stream()
                .flatMap(entry -> entry.getMeal().getIngredients().stream())
                .collect(Collectors.toMap(
                        MealIngredient::getProduct,
                        MealIngredient::getQuantity,
                        BigDecimal::add,
                        LinkedHashMap::new
                ));
    }

    @Transient
    public BigDecimal getTotalCost() {
        return getShoppingList().entrySet().stream()
                .map(e -> e.getKey().getPricePerUnit().multiply(e.getValue()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    // â”€â”€ Helper â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    public void addEntry(MealPlanEntry entry) {
        entries.add(entry);
        entry.setMealPlan(this);
    }

    public void removeEntry(MealPlanEntry entry) {
        entries.remove(entry);
        entry.setMealPlan(null);
    }
}

