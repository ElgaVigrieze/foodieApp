package com.foodie.controller.form;

import com.foodie.model.MealCategory;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

/** Create/edit meal form ({@code id} is null when creating). */
public record MealForm(
        Long id,
        @NotBlank @Size(max = 255) String name,
        @NotNull MealCategory category,
        @NotNull @Positive Integer servings,
        String recipe,
        @Size(max = 1000) String recipeUrl,
        @Size(max = 200) List<Long> productIds,
        @Size(max = 200) List<BigDecimal> quantities
) {
    /** The two lists are parallel: every product needs a quantity. */
    @AssertTrue(message = "each ingredient needs a quantity")
    public boolean isIngredientListsConsistent() {
        int products = productIds == null ? 0 : productIds.size();
        int amounts = quantities == null ? 0 : quantities.size();
        return products == amounts;
    }

    public boolean hasIngredients() {
        return productIds != null && quantities != null && !productIds.isEmpty();
    }
}
