package com.foodie.model.db;

import com.foodie.model.OwnedEntity;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * A persisted shopping list item that can be manually adjusted.
 * Generated from the meal plan but editable by the user.
 */
@Entity
@Table(name = "shopping_list_items")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class ShoppingListItem implements OwnedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    private LocalDate weekStart;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "product_id", nullable = false)
    @NotNull
    private Product product;

    /** Quantity calculated from the meal plan. */
    @NotNull
    @PositiveOrZero
    private BigDecimal calculatedQuantity;

    /** User-adjusted quantity (this is what they actually buy). */
    @NotNull
    @PositiveOrZero
    private BigDecimal adjustedQuantity;

    /** Whether this item has been checked off / purchased. */
    @Builder.Default
    private boolean checked = false;

    /** Optional short comment for this item (max 50 chars). */
    @Column(length = 50)
    private String comment;

    /** Who added the item. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id")
    private AppUser owner;

    /** The household whose list this item belongs to (null only for old items V3 could not attribute). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "household_id")
    private Household household;

    @Transient
    public BigDecimal getCost() {
        return adjustedQuantity.multiply(product.getPricePerUnit());
    }
}