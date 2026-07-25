package com.foodie.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.*;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * A product used in a meal with a specific quantity.
 */
@Entity
@Table(name = "meal_ingredients")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class MealIngredient {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "meal_id", nullable = false)
    private Meal meal;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "product_id", nullable = false)
    @NotNull
    private Product product;

    /** Quantity in the product's unit (kg, liters, or pieces). */
    @NotNull
    @Positive
    private BigDecimal quantity;

    // ── Computed ───────────────────────────────────────────────────────────

    /**
     * Cost of this ingredient line = quantity * pricePerUnit.
     */
    @Transient
    public BigDecimal getCost() {
        if (product == null || quantity == null) return BigDecimal.ZERO;
        return quantity.multiply(product.getPricePerUnit());
    }

    /**
     * Get the amount of a specific nutrient contributed by this ingredient.
     * For KG/LITER: nutrition is per 100g/100ml, quantity is in kg/L → multiply by quantity*10
     * For PIECE: nutrition is per piece → multiply by quantity
     */
    @Transient
    public BigDecimal getNutrientAmount(NutrientType type) {
        if (product == null || quantity == null) return BigDecimal.ZERO;

        BigDecimal nutritionPer = switch (type) {
            case CALORIES -> product.getCalories();
            case PROTEIN  -> product.getProtein();
            case CARBS    -> product.getCarbs();
            case FAT      -> product.getFat();
            case FIBER    -> product.getFiber();
            case SUGAR    -> product.getSugar();
        };

        if (nutritionPer == null) return BigDecimal.ZERO;

        if (product.getUnit() == Unit.PIECE) {
            // Nutrition is per 1 piece, quantity is number of pieces
            return nutritionPer.multiply(quantity);
        } else {
            // Nutrition is per 100g/100ml, quantity is in kg/L
            // 1 kg = 1000g → quantity * 10 gives "number of 100g portions"
            return nutritionPer.multiply(quantity.multiply(BigDecimal.TEN));
        }
    }
}
