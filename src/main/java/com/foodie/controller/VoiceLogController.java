package com.foodie.controller;

import com.foodie.model.Meal;
import com.foodie.model.MealSlot;
import com.foodie.model.Product;
import com.foodie.service.FoodLogService;
import com.foodie.service.MealService;
import com.foodie.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/voice")
@RequiredArgsConstructor
public class VoiceLogController {

    private final MealService mealService;
    private final ProductService productService;
    private final FoodLogService foodLogService;

    @PostMapping("/parse")
    public Map<String, Object> parse(@RequestBody Map<String, String> body) {
        String text = body.getOrDefault("text", "").toLowerCase().replaceAll("[^a-z0-9\\s.]", "").replaceAll("\\.", "").trim();
        Map<String, Object> result = new LinkedHashMap<>();

        if (text.isEmpty()) {
            result.put("error", "No text provided");
            return result;
        }

        // Detect slot
        MealSlot slot = detectSlot(text);

        // Detect quantity
        BigDecimal quantity = detectQuantity(text);

        // Try to match a meal first
        List<Meal> meals = mealService.findAll();
        Meal matchedMeal = findBestMatch(meals, text);

        if (matchedMeal != null) {
            result.put("type", "meal");
            result.put("mealId", matchedMeal.getId());
            result.put("mealName", matchedMeal.getName());
            result.put("servings", quantity != null ? quantity : BigDecimal.ONE);
            result.put("slot", slot);
            result.put("parsed", true);
            return result;
        }

        // Try to match a product
        List<Product> products = productService.findAll();
        Product matchedProduct = findBestProductMatch(products, text);

        if (matchedProduct != null) {
            result.put("type", "product");
            result.put("productId", matchedProduct.getId());
            result.put("productName", matchedProduct.getName());
            result.put("quantity", quantity != null ? quantity : new BigDecimal("0.1"));
            result.put("slot", slot);
            result.put("parsed", true);
            return result;
        }

        result.put("parsed", false);
        result.put("error", "Could not match: " + text + " (meals=" + meals.size() + ", products=" + products.size() + ")");
        return result;
    }

    @PostMapping("/submit")
    public Map<String, Object> submit(@RequestBody Map<String, Object> body) {
        Map<String, Object> result = new LinkedHashMap<>();
        try {
            String type = (String) body.get("type");
            LocalDate date = LocalDate.parse((String) body.get("date"));
            MealSlot slot = body.get("slot") != null ? MealSlot.valueOf((String) body.get("slot")) : null;

            if ("meal".equals(type)) {
                Long mealId = Long.valueOf(body.get("mealId").toString());
                BigDecimal servings = new BigDecimal(body.get("servings").toString());
                foodLogService.addEntry(date, mealId, servings, slot);
                result.put("success", true);
            } else if ("product".equals(type)) {
                Long productId = Long.valueOf(body.get("productId").toString());
                BigDecimal quantity = new BigDecimal(body.get("quantity").toString());
                foodLogService.addProductEntry(date, productId, quantity, slot);
                result.put("success", true);
            }
        } catch (Exception e) {
            result.put("success", false);
            result.put("error", e.getMessage());
        }
        return result;
    }

    private MealSlot detectSlot(String text) {
        if (text.contains("breakfast") || text.contains("morning")) return MealSlot.BREAKFAST;
        if (text.contains("lunch") || text.contains("midday")) return MealSlot.LUNCH;
        if (text.contains("dinner") || text.contains("evening")) return MealSlot.DINNER;
        if (text.contains("snack")) return MealSlot.SNACK;
        return null;
    }

    private BigDecimal detectQuantity(String text) {
        // Match patterns like "2 servings", "0.5 srv", "3", "1.5 portions", "200 grams", "0.3 kg"
        java.util.regex.Pattern p = java.util.regex.Pattern.compile("(\\d+\\.?\\d*)\\s*(servings?|srv|portions?|kg|g|grams?|liters?|l|pieces?|pc)?");
        java.util.regex.Matcher m = p.matcher(text);
        if (m.find()) {
            BigDecimal val = new BigDecimal(m.group(1));
            String unit = m.group(2);
            if (unit != null && (unit.startsWith("g") && !unit.equals("grams"))) {
                // Convert grams to kg
                return val.divide(BigDecimal.valueOf(1000), 4, java.math.RoundingMode.HALF_UP);
            }
            if (unit != null && unit.startsWith("g")) {
                return val.divide(BigDecimal.valueOf(1000), 4, java.math.RoundingMode.HALF_UP);
            }
            return val;
        }
        return null;
    }


    private Meal findBestMatch(List<Meal> meals, String text) {
        Meal best = null;
        int bestScore = 0;
        for (Meal meal : meals) {
            String mealLower = meal.getName().toLowerCase();
            String[] words = mealLower.split("\\s+");
            int score = 0;
            for (String word : words) {
                if (word.length() > 2 && text.contains(word)) {
                    score += word.length();
                }
            }
            if (score > bestScore && score >= 4) {
                bestScore = score;
                best = meal;
            }
        }
        return best;
    }
        private Product findBestProductMatch(List<Product> products, String text) {
        String cleaned = text.replaceAll("\\d+\\.?\\d*\\s*(kg|g|grams?|liters?|l|pieces?|pc|servings?|srv)?", "")
                .replaceAll("(breakfast|lunch|dinner|snack|morning|evening|midday|for|of)", "")
                .trim();

        Product best = null;
        int bestScore = 0;

        for (Product product : products) {
            String prodLower = product.getName().toLowerCase();
            if (cleaned.contains(prodLower) || prodLower.contains(cleaned)) {
                int score = prodLower.length();
                if (score > bestScore) {
                    bestScore = score;
                    best = product;
                }
            }
            // Also check individual words
            String[] words = prodLower.split("\\s+");
            int wordScore = 0;
            for (String word : words) {
                if (word.length() > 2 && cleaned.contains(word)) {
                    wordScore += word.length();
                }
            }
            if (wordScore > bestScore && wordScore >= 4) {
                bestScore = wordScore;
                best = product;
            }
        }
        return best;
    }
}