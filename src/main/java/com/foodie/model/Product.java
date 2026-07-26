package com.foodie.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.*;

import java.math.BigDecimal;

/**
 * A product (ingredient) with price and nutrition info.
 * Nutrition values are per 100g/100ml for KG/LITER, or per 1 piece for PIECE.
 */
@Entity
@Table(name = "products")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    private String name;

    @Enumerated(EnumType.STRING)
    private ProductCategory category;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "household_id")
    private Household household;

    @NotNull
    @Enumerated(EnumType.STRING)
    private Unit unit;

    /** Price per 1 kg, 1 liter, or 1 piece. */
    @NotNull
    @PositiveOrZero
    private BigDecimal pricePerUnit;

    // â”€â”€ Nutrition (per 100g/100ml or per 1 piece) â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    @PositiveOrZero
    private BigDecimal calories;

    @PositiveOrZero
    private BigDecimal protein;

    @PositiveOrZero
    private BigDecimal carbs;

    @PositiveOrZero
    private BigDecimal fat;

    @PositiveOrZero
    private BigDecimal fiber;

    @PositiveOrZero
    private BigDecimal sugar;

    /** Net carbs = carbs - fiber (computed, not persisted). */
    @Transient
    public BigDecimal getNetCarbs() {
        if (carbs == null || fiber == null) return carbs;
        return carbs.subtract(fiber);
    }
}

