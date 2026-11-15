package com.foodie.controller;

import com.foodie.controller.dto.MatchedIngredient;
import com.foodie.controller.dto.ProductResolutionRequest;
import com.foodie.exception.BadRequestException;
import com.foodie.exception.ConflictException;
import com.foodie.exception.ExternalServiceException;
import com.foodie.exception.NotFoundException;
import com.foodie.exception.UnsafeUrlException;
import com.foodie.model.MealCategory;
import com.foodie.model.RecipeExtract;
import com.foodie.service.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.math.BigDecimal;
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

    private final RecipeScraperService scraperService;
    private final RecipeIngredientMatcherService matcherService;
    private final MealService mealService;

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
            List<MatchedIngredient> matched = matcherService.matchAll(extract);

            populatePreviewModel(model, extract, matched, recipeUrl.trim());
            return "meals/import-preview";

        } catch (UnsafeUrlException | BadRequestException | ExternalServiceException e) {
            // Expected failures with a user-readable message (blocked URL, AI service down, ...).
            log.warn("Recipe URL import failed for {}: {}", sanitize(recipeUrl), e.getMessage());
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/meals/import";
        }
        // Anything else is a bug: WebExceptionHandler logs it and sends the user back here.
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
            List<MatchedIngredient> matched = matcherService.matchAll(extract);

            populatePreviewModel(model, extract, matched, null);
            return "meals/import-preview";

        } catch (IOException e) {
            log.warn("Could not read uploaded recipe photo: {}", e.getMessage(), e);
            redirectAttributes.addFlashAttribute("error",
                    "Could not read the uploaded image. Please try again.");
            return "redirect:/meals/import";
        } catch (BadRequestException | ExternalServiceException e) {
            log.warn("Recipe photo import failed: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/meals/import";
        }
    }

    // ── Step 2: confirm & save ─────────────────────────────────────────────

    @PostMapping("/confirm")
    public String confirm(
            @RequestParam String mealName,
            @RequestParam MealCategory mealCategory,
            @RequestParam int mealServings,
            @RequestParam(required = false) String mealInstructions,
            @RequestParam(required = false) String recipeUrl,
            @RequestParam(name = "productIds") List<Long> productIds,
            @RequestParam(name = "productNames") List<String> productNames,
            @RequestParam(name = "productUnits") List<String> productUnits,
            @RequestParam(name = "quantities") List<BigDecimal> quantities,
            RedirectAttributes redirectAttributes) {

        try {
            // Resolve product IDs: existing ones are used directly; new ones are created
            ProductResolutionRequest resolutionRequest = new ProductResolutionRequest(
                    productIds, productNames, productUnits);
            List<Long> resolvedIds = matcherService.resolveProductIds(resolutionRequest);

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

        } catch (BadRequestException | ConflictException | NotFoundException e) {
            log.warn("Recipe import confirm rejected: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/meals/import";
        } catch (RuntimeException e) {
            // The form was posted from the preview page, which cannot be reloaded with GET, so the
            // generic handler's "back to the referer" would not work: redirect explicitly.
            log.error("Recipe import confirm failed: {}", e.getMessage(), e);
            redirectAttributes.addFlashAttribute("error",
                    "Failed to save the recipe. Please check the details and try again.");
            return "redirect:/meals/import";
        }
    }

    /** Strips line breaks from user-supplied text before it is logged. */
    private static String sanitize(String value) {
        return value == null ? null : value.replaceAll("[\\r\\n]", " ");
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private void populatePreviewModel(
            Model model,
            RecipeExtract extract,
            List<MatchedIngredient> matched,
            String recipeUrl) {

        model.addAttribute("extract", extract);
        model.addAttribute("matched", matched);
        model.addAttribute("recipeUrl", recipeUrl != null ? recipeUrl : "");
        model.addAttribute("categories", MealCategory.values());
        model.addAttribute("allProducts", matcherService.getAllProducts());

        // Count new products so the UI can warn the user
        long newCount = matched.stream().filter(MatchedIngredient::isNew).count();
        model.addAttribute("newProductCount", newCount);
    }
}