package com.foodie.controller;

import com.foodie.model.MealCategory;
import com.foodie.service.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Handles the recipe import flow:
 *
 *  GET  /meals/import               → show the import form (URL or photo)
 *  POST /meals/import/from-url      → scrape URL, match ingredients → preview
 *  POST /meals/import/from-photo    → analyse photo, match ingredients → preview
 *  POST /meals/import/confirm       → persist products + meal → redirect to meals list
 */
@Controller
@RequestMapping("/meals/import")
@RequiredArgsConstructor
@Slf4j
public class RecipeImportController {

    private final RecipeScraperService      scraperService;
    private final RecipeIngredientMatcherService matcherService;
    private final MealService               mealService;

    // ── Import form ────────────────────────────────────────────────────────

    @GetMapping
    public String showImportForm(Model model) {
        model.addAttribute("cloudflareAvailable", scraperService.isAvailable());
        return "meals/import";
    }

    // ── Step 1a: import from URL ───────────────────────────────────────────

    @PostMapping("/from-url")
    public String importFromUrl(
            @RequestParam String recipeUrl,
            Model model,
            RedirectAttributes redirectAttributes) {

        if (recipeUrl == null || recipeUrl.isBlank()) {
            redirectAttributes.addFlashAttribute("error", "Please provide a URL.");
            return "redirect:/meals/import";
        }

        try {
            RecipeExtract extract = scraperService.extractFromUrl(recipeUrl.trim());
            List<RecipeIngredientMatcherService.MatchedIngredient> matched =
                    matcherService.matchAll(extract);

            populatePreviewModel(model, extract, matched, recipeUrl.trim());
            return "meals/import-preview";

        } catch (Exception e) {
            log.error("Recipe URL import failed for {}: {}", recipeUrl, e.getMessage(), e);
            redirectAttributes.addFlashAttribute("error",
                    "Could not extract recipe from that URL. Try pasting the recipe text manually, or upload a screenshot.");
            return "redirect:/meals/import";
        }
    }

    // ── Step 1b: import from photo ─────────────────────────────────────────

    @PostMapping("/from-photo")
    public String importFromPhoto(
            @RequestParam MultipartFile photo,
            Model model,
            RedirectAttributes redirectAttributes) {

        if (photo == null || photo.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Please select an image file.");
            return "redirect:/meals/import";
        }

        String contentType = photo.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            redirectAttributes.addFlashAttribute("error", "Please upload an image file (jpg, png, webp).");
            return "redirect:/meals/import";
        }

        try {
            byte[] bytes = photo.getBytes();
            RecipeExtract extract = scraperService.extractFromPhoto(bytes, contentType);
            List<RecipeIngredientMatcherService.MatchedIngredient> matched =
                    matcherService.matchAll(extract);

            populatePreviewModel(model, extract, matched, null);
            return "meals/import-preview";

        } catch (Exception e) {
            log.error("Recipe photo import failed: {}", e.getMessage(), e);
            redirectAttributes.addFlashAttribute("error",
                    "Could not extract recipe from the photo. Make sure the image contains a clear recipe.");
            return "redirect:/meals/import";
        }
    }

    // ── Step 2: confirm & save ─────────────────────────────────────────────

    /**
     * The preview form POSTs back arrays of product IDs and quantities.
     * New products (id = 0) are rebuilt from the name/unit params and persisted here.
     *
     * Form fields:
     *   mealName        – editable meal name
     *   mealCategory    – editable category
     *   mealServings    – editable servings
     *   mealInstructions – editable instructions
     *   recipeUrl       – original source URL (may be empty)
     *   productIds[]    – existing product id, or 0 for new
     *   productNames[]  – display name (used to create new products)
     *   productUnits[]  – unit string for new products (KG / LITER / PIECE)
     *   quantities[]    – quantity per ingredient
     */
    @PostMapping("/confirm")
    public String confirm(
            @RequestParam String mealName,
            @RequestParam MealCategory mealCategory,
            @RequestParam int mealServings,
            @RequestParam(required = false) String mealInstructions,
            @RequestParam(required = false) String recipeUrl,
            @RequestParam(name = "productIds")    List<Long>       productIds,
            @RequestParam(name = "productNames")  List<String>     productNames,
            @RequestParam(name = "productUnits")  List<String>     productUnits,
            @RequestParam(name = "quantities")    List<BigDecimal> quantities,
            RedirectAttributes redirectAttributes) {

        try {
            // Resolve product IDs: existing ones are used directly;
            // new ones (id == 0) are created on the fly via ProductService.
            List<Long> resolvedIds = new ArrayList<>();

            for (int i = 0; i < productIds.size(); i++) {
                long pid = productIds.get(i);

                if (pid > 0) {
                    resolvedIds.add(pid);
                } else {
                    // New product — build a minimal entity and let ProductService fill nutrition
                    com.foodie.model.Product newProduct = com.foodie.model.Product.builder()
                            .name(productNames.get(i))
                            .unit(parseUnit(productUnits.get(i)))
                            .pricePerUnit(BigDecimal.ZERO)
                            .build();
                    com.foodie.model.Product saved = matcherService.saveNewProduct(newProduct);
                    resolvedIds.add(saved.getId());
                    log.info("Created product '{}' (id={})", saved.getName(), saved.getId());
                }
            }

            mealService.createMeal(
                    mealName.trim(),
                    mealCategory,
                    Math.max(1, mealServings),
                    mealInstructions,
                    recipeUrl,
                    resolvedIds,
                    quantities
            );

            redirectAttributes.addFlashAttribute("success",
                    "Recipe imported! \"" + mealName.trim() + "\" has been saved.");
            return "redirect:/meals";

        } catch (Exception e) {
            log.error("Recipe import confirm failed: {}", e.getMessage(), e);
            redirectAttributes.addFlashAttribute("error",
                    "Failed to save the recipe: " + e.getMessage());
            return "redirect:/meals/import";
        }
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private void populatePreviewModel(
            Model model,
            RecipeExtract extract,
            List<RecipeIngredientMatcherService.MatchedIngredient> matched,
            String recipeUrl) {

        model.addAttribute("extract",    extract);
        model.addAttribute("matched",    matched);
        model.addAttribute("recipeUrl",  recipeUrl != null ? recipeUrl : "");
        model.addAttribute("categories", MealCategory.values());
        model.addAttribute("allProducts", matcherService.getAllProducts());

        // Count new products so the UI can warn the user
        long newCount = matched.stream().filter(RecipeIngredientMatcherService.MatchedIngredient::isNew).count();
        model.addAttribute("newProductCount", newCount);
    }

    private com.foodie.model.Unit parseUnit(String unit) {
        if (unit == null) return com.foodie.model.Unit.KG;
        return switch (unit.toUpperCase()) {
            case "LITER", "L" -> com.foodie.model.Unit.LITER;
            case "PIECE", "PC" -> com.foodie.model.Unit.PIECE;
            default -> com.foodie.model.Unit.KG;
        };
    }
}
