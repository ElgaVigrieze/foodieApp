package com.foodie.controller.dto;

import com.foodie.model.db.Product;
import com.foodie.model.RecipeExtract;

import java.math.BigDecimal;

/**
 * Result for a single ingredient line — carries enough info for the
 * preview page and for the final save step.
 */
public record MatchedIngredient(
        RecipeExtract.IngredientLine line,
        Product product,
        BigDecimal quantity,
        boolean isNew
) {}