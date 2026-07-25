package com.foodie.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.*;

import java.math.BigDecimal;
import java.time.DayOfWeek;

/**
 * A standalone product added to the meal plan (not part of a meal).
 * E.g., a cucumber or tomato for a simple side.
 */
@Entity
@Table(name = "meal_plan_products")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class MealPlanProduct {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "meal_plan_id", nullable = false)
    private MealPlan mealPlan;

    @NotNull
    @Enumerated(EnumType.STRING)
    private DayOfWeek dayOfWeek;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "product_id", nullable = false)
    @NotNull
    private Product product;

    /** Quantity in the product's unit (kg, L, or pieces). */
    @NotNull
    @Positive
    private BigDecimal quantity;

    @Transient
    public BigDecimal getCost() {
        return quantity.multiply(product.getPricePerUnit());
    }
}
