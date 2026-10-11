package com.foodie.service;

import com.foodie.controller.dto.MatchedIngredient;
import com.foodie.controller.dto.ProductResolutionRequest;
import com.foodie.model.db.Product;
import com.foodie.model.RecipeExtract;
import com.foodie.model.Unit;
import com.foodie.util.TextMatchingUtils;
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
 * <p>Matching strategy uses word-overlap scoring via {@link TextMatchingUtils}:
 * <ol>
 *   <li>Split both the ingredient name and every product name into words.</li>
 *   <li>Score = total length of shared words longer than 2 chars.</li>
 *   <li>Accept the best match when score ≥ {@link TextMatchingUtils#MIN_MATCH_SCORE}.</li>
 *   <li>If no match → create a new Product and auto-fill nutrition via Open Food Facts.</li>
 * </ol>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RecipeIngredientMatcherService {

    private final ProductService productService;

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

    /**
     * Resolve product IDs from the import preview form.
     * Existing products (id > 0) are returned as-is.
     * New products (id == 0) are created and their new IDs are returned.
     *
     * @param request the resolution request containing product IDs, names, and units
     * @return list of resolved product IDs (all existing in the database)
     */
    @Transactional
    public List<Long> resolveProductIds(ProductResolutionRequest request) {
        List<Long> resolvedIds = new ArrayList<>();

        for (int i = 0; i < request.productIds().size(); i++) {
            long pid = request.productIds().get(i);

            if (pid > 0) {
                resolvedIds.add(pid);
            } else {
                // New product — build and save
                Product newProduct = Product.builder()
                        .name(request.productNames().get(i))
                        .unit(inferUnit(request.productUnits().get(i)))
                        .pricePerUnit(BigDecimal.ZERO)
                        .build();
                Product saved = productService.save(newProduct);
                resolvedIds.add(saved.getId());
                log.info("Created product '{}' (id={})", saved.getName(), saved.getId());
            }
        }

        return resolvedIds;
    }

    /**
     * Expose all household products for the preview page's product picker.
     */
    public List<Product> getAllProducts() {
        return productService.findAll();
    }

    // ── Matching logic ─────────────────────────────────────────────────────

    private MatchedIngredient match(RecipeExtract.IngredientLine line, List<Product> products) {
        Product best = TextMatchingUtils.findBestMatch(products, line.name(), Product::getName);

        if (best != null) {
            log.debug("Matched '{}' → '{}'", line.name(), best.getName());
            return new MatchedIngredient(line, best, line.quantity(), false);
        }

        // No match — build a transient Product for the preview (not yet persisted)
        log.info("No match for '{}' — will create new product", line.name());
        Product newProduct = buildNewProduct(line);
        return new MatchedIngredient(line, newProduct, line.quantity(), true);
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

    /**
     * Infer the appropriate {@link Unit} from a raw unit string.
     * Handles various unit formats from recipes and form submissions.
     */
    private Unit inferUnit(String rawUnit) {
        if (rawUnit == null || rawUnit.isBlank()) return Unit.KG;
        return switch (rawUnit.toLowerCase().trim()) {
            case "piece", "pieces", "pc", "pcs",
                 "slice", "slices", "clove", "cloves",
                 "whole", "large", "medium", "small" -> Unit.PIECE;
            case "l", "liter", "liters", "litre", "litres",
                 "ml", "milliliter", "milliliters",
                 "cup", "cups", "tbsp", "tablespoon",
                 "tsp", "teaspoon" -> Unit.LITER;
            default -> Unit.KG;
        };
    }

    private String capitalise(String s) {
        if (s == null || s.isBlank()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}