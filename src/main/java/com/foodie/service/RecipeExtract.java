package com.foodie.service;

import java.math.BigDecimal;
import java.util.List;

/**
 * Structured recipe data extracted from a URL or photo by the AI.
 */
public record RecipeExtract(
        String name,
        String category,        // raw string matching MealCategory enum name
        int servings,
        String instructions,
        List<IngredientLine> ingredients
) {

    /**
     * A single ingredient line as parsed from the recipe source.
     * quantity is already normalised to the product's storage unit:
     *   - grams/ml → converted to kg/L  (÷ 1000)
     *   - pieces    → kept as-is
     */
    public record IngredientLine(
            String name,
            BigDecimal quantity,
            String rawUnit   // original unit string from AI ("g", "ml", "piece", etc.)
    ) {}
}
