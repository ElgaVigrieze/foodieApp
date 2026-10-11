package com.foodie.service;

import com.foodie.controller.dto.VoiceParseResult;
import com.foodie.controller.dto.VoiceSubmitRequest;
import com.foodie.controller.dto.VoiceSubmitResult;
import com.foodie.model.db.Meal;
import com.foodie.model.MealSlot;
import com.foodie.model.db.Product;
import com.foodie.util.TextMatchingUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Service for voice-based food logging.
 * Parses natural language text to identify meals/products and logs them.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class VoiceLogService {

    private static final Pattern QUANTITY_PATTERN = Pattern.compile(
            "(\\d+\\.?\\d*)\\s*(servings?|srv|portions?|kg|g|grams?|liters?|l|pieces?|pc)?");

    private final MealService mealService;
    private final ProductService productService;
    private final FoodLogService foodLogService;

    /**
     * Parse voice input text to identify a meal or product.
     *
     * @param text the raw voice input
     * @return parse result with matched item details
     */
    public VoiceParseResult parse(String text) {
        String normalized = normalizeText(text);

        if (normalized.isEmpty()) {
            return VoiceParseResult.error("No text provided");
        }

        MealSlot slot = detectSlot(normalized);
        BigDecimal quantity = detectQuantity(normalized);

        // Try to match a meal first
        List<Meal> meals = mealService.findAll();
        Meal matchedMeal = TextMatchingUtils.findBestMatch(meals, normalized, Meal::getName);

        if (matchedMeal != null) {
            return VoiceParseResult.meal(
                    matchedMeal.getId(),
                    matchedMeal.getName(),
                    quantity != null ? quantity : BigDecimal.ONE,
                    slot
            );
        }

        // Try to match a product
        List<Product> products = productService.findAll();
        String cleanedText = cleanTextForProductMatch(normalized);
        Product matchedProduct = TextMatchingUtils.findBestMatch(products, cleanedText, Product::getName);

        if (matchedProduct != null) {
            return VoiceParseResult.product(
                    matchedProduct.getId(),
                    matchedProduct.getName(),
                    quantity != null ? quantity : new BigDecimal("0.1"),
                    slot
            );
        }

        return VoiceParseResult.notFound("Could not match: " + text + " (meals=" + meals.size() + ", products=" + products.size() + ")");
    }

    /**
     * Submit a parsed voice log entry.
     *
     * @param request the submit request with parsed data
     * @return submit result indicating success or failure
     */
    public VoiceSubmitResult submit(VoiceSubmitRequest request) {
        try {
            if ("meal".equals(request.type())) {
                foodLogService.addEntry(request.date(), request.mealId(), request.servings(), request.slot());
                return VoiceSubmitResult.ok();
            } else if ("product".equals(request.type())) {
                foodLogService.addProductEntry(request.date(), request.productId(), request.quantity(), request.slot());
                return VoiceSubmitResult.ok();
            } else {
                return VoiceSubmitResult.fail("Unknown type: " + request.type());
            }
        } catch (Exception e) {
            log.warn("Failed to submit voice log: {}", e.getMessage());
            return VoiceSubmitResult.fail(e.getMessage());
        }
    }

    // ── Private helpers ────────────────────────────────────────────────────

    private String normalizeText(String text) {
        if (text == null) return "";
        return text.toLowerCase()
                .replaceAll("[^a-z0-9\\s.]", "")
                .replaceAll("\\.", "")
                .trim();
    }

    private MealSlot detectSlot(String text) {
        if (text.contains("breakfast") || text.contains("morning")) return MealSlot.BREAKFAST;
        if (text.contains("lunch") || text.contains("midday")) return MealSlot.LUNCH;
        if (text.contains("dinner") || text.contains("evening")) return MealSlot.DINNER;
        if (text.contains("snack")) return MealSlot.SNACK;
        return null;
    }

    private BigDecimal detectQuantity(String text) {
        Matcher m = QUANTITY_PATTERN.matcher(text);
        if (m.find()) {
            BigDecimal val = new BigDecimal(m.group(1));
            String unit = m.group(2);

            // Convert grams to kg
            if (unit != null && (unit.startsWith("g") || unit.equals("grams"))) {
                return val.divide(BigDecimal.valueOf(1000), 4, RoundingMode.HALF_UP);
            }
            return val;
        }
        return null;
    }

    /**
     * Clean text by removing quantities, units, and slot keywords for product matching.
     */
    private String cleanTextForProductMatch(String text) {
        return text
                .replaceAll("\\d+\\.?\\d*\\s*(kg|g|grams?|liters?|l|pieces?|pc|servings?|srv)?", "")
                .replaceAll("(breakfast|lunch|dinner|snack|morning|evening|midday|for|of)", "")
                .trim();
    }
}