package com.foodie.service;

import com.foodie.model.Product;
import com.foodie.model.Unit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Maps each extracted {@link RecipeExtract.IngredientLine} to an existing
 * household {@link Product}, or creates a new one if no close match is found.
 *
 * <p>Matching strategy (same word-score approach as VoiceLogController):
 * <ol>
 *   <li>Split both the ingredient name and every product name into words.</li>
 *   <li>Score = total length of shared words longer than 2 chars.</li>
 *   <li>Accept the best match when score ≥ 4 (avoids false positives on tiny words).</li>
 *   <li>If no match → create a new Product and auto-fill nutrition via Open Food Facts.</li>
 * </ol>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RecipeIngredientMatcherService {

    private final ProductService productService;

    /**
     * Result for a single ingredient line — carries enough info for the
     * preview page and for the final save step.
     */
    public record MatchedIngredient(
            RecipeExtract.IngredientLine line,   // original AI extract
            Product product,                      // matched or newly-created Product
            BigDecimal quantity,                  // normalised quantity (in product's unit)
            boolean isNew                         // true → will be created on confirm
    ) {}

    // ── Public API ─────────────────────────────────────────────────────────

    /**
     * Match (or plan to create) products for every ingredient in the extract.
     * Does NOT persist new products yet — that happens in {@link #confirmAndSave}.
     * This allows the preview page to show the user what will be created before
     * anything is written to the database.
     */
    public List<MatchedIngredient> matchAll(RecipeExtract extract) {
        List<Product> existingProducts = productService.findAll();
        List<MatchedIngredient> results = new ArrayList<>();

        for (RecipeExtract.IngredientLine line : extract.ingredients()) {
            MatchedIngredient matched = match(line, existingProducts);
            results.add(matched);
        }
        return results;
    }

    /**
     * Persist any new products that were planned during {@link #matchAll},
     * then return the final list ready for {@link MealService#createMeal}.
     *
     * Called when the user clicks "Confirm & Save" on the preview page.
     */
    @Transactional
    public List<MatchedIngredient> confirmAndSave(List<MatchedIngredient> matched) {
        List<MatchedIngredient> saved = new ArrayList<>();
        for (MatchedIngredient mi : matched) {
            if (mi.isNew()) {
                // Persist the product (ProductService.save auto-fills nutrition via Open Food Facts)
                Product persisted = productService.save(mi.product());
                log.info("Created new product: {} (id={})", persisted.getName(), persisted.getId());
                saved.add(new MatchedIngredient(mi.line(), persisted, mi.quantity(), true));
            } else {
                saved.add(mi);
            }
        }
        return saved;
    }

    /**
     * Thin delegate used by {@link com.foodie.controller.RecipeImportController}
     * to persist a single new product at confirm-time.
     */
    @Transactional
    public Product saveNewProduct(Product product) {
        return productService.save(product);
    }

    // ── Matching logic ─────────────────────────────────────────────────────

    private MatchedIngredient match(RecipeExtract.IngredientLine line, List<Product> products) {
        String ingredientLower = line.name().toLowerCase();

        Product best      = null;
        int     bestScore = 0;

        for (Product product : products) {
            int score = wordOverlapScore(ingredientLower, product.getName().toLowerCase());
            if (score > bestScore) {
                bestScore = score;
                best = product;
            }
        }

        // Accept match only if score is meaningful (avoids matching "oil" to "olive oil cake")
        if (best != null && bestScore >= 4) {
            log.debug("Matched '{}' → '{}' (score={})", line.name(), best.getName(), bestScore);
            return new MatchedIngredient(line, best, line.quantity(), false);
        }

        // No match — build a transient Product for the preview (not yet persisted)
        log.info("No match for '{}' — will create new product", line.name());
        Product newProduct = buildNewProduct(line);
        return new MatchedIngredient(line, newProduct, line.quantity(), true);
    }

    /**
     * Word-overlap score: sum of lengths of shared words longer than 2 characters.
     * Identical to the approach in VoiceLogController.
     */
    private int wordOverlapScore(String ingredient, String productName) {
        String[] ingredientWords = ingredient.split("\\s+");
        int score = 0;
        for (String word : ingredientWords) {
            if (word.length() > 2 && productName.contains(word)) {
                score += word.length();
            }
        }
        // Also check full substring in both directions for short names
        if (productName.contains(ingredient) || ingredient.contains(productName)) {
            score = Math.max(score, productName.length());
        }
        return score;
    }

    /**
     * Build a transient (not yet persisted) Product from an ingredient line.
     * Determines the best {@link Unit} from the raw unit string.
     * Nutrition will be auto-filled by Open Food Facts when the product is saved.
     */
    private Product buildNewProduct(RecipeExtract.IngredientLine line) {
        Unit unit = inferUnit(line.rawUnit());
        // Capitalise the ingredient name
        String name = capitalise(line.name().trim());

        return Product.builder()
                .name(name)
                .unit(unit)
                .pricePerUnit(BigDecimal.ZERO)   // user can update price later
                .build();
        // household is set by ProductService.save() via CurrentUserService
    }

    private Unit inferUnit(String rawUnit) {
        if (rawUnit == null) return Unit.KG;
        return switch (rawUnit.toLowerCase().trim()) {
            case "piece", "pieces", "pc", "pcs",
                 "slice", "slices", "clove", "cloves",
                 "whole", "large", "medium", "small" -> Unit.PIECE;
            case "l", "liter", "liters", "litre", "litres",
                 "ml", "milliliter", "milliliters",
                 "cup", "cups", "tbsp", "tablespoon",
                 "tsp", "teaspoon"                   -> Unit.LITER;
            default                                  -> Unit.KG;
        };
    }

    private String capitalise(String s) {
        if (s == null || s.isBlank()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
