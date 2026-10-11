package com.foodie.controller;

import com.foodie.controller.dto.PhotoEntryRequest;
import com.foodie.model.MealSlot;
import com.foodie.service.FoodLogService;
import com.foodie.service.PhotoNutritionService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/photo")
@RequiredArgsConstructor
public class PhotoLogController {

    private final PhotoNutritionService photoNutritionService;
    private final FoodLogService foodLogService;

    @PostMapping("/analyze")
    public Map<String, Object> analyze(@RequestParam("image") MultipartFile image) {
        Map<String, Object> result = new LinkedHashMap<>();

        if (!photoNutritionService.isAvailable()) {
            result.put("error", "Photo analysis not configured. Set CLOUDFLARE_ACCOUNT_ID and CLOUDFLARE_API_TOKEN.");
            return result;
        }

        if (image.isEmpty()) {
            result.put("error", "No image provided");
            return result;
        }

        try {
            byte[] bytes = image.getBytes();
            String mimeType = image.getContentType() != null ? image.getContentType() : "image/jpeg";

            PhotoNutritionService.NutritionEstimate estimate = photoNutritionService.analyzePhoto(bytes, mimeType);

            if (estimate.isValid()) {
                result.put("success", true);
                result.put("description", estimate.description());
                result.put("calories", estimate.calories());
                result.put("protein", estimate.protein());
                result.put("carbs", estimate.carbs());
                result.put("fat", estimate.fat());
                result.put("fiber", estimate.fiber());
                result.put("weightGrams", estimate.estimatedWeightGrams());
            } else {
                result.put("success", false);
                result.put("error", estimate.description());
            }
        } catch (Exception e) {
            result.put("success", false);
            result.put("error", "Failed to process image: " + e.getMessage());
        }
        return result;
    }

    @PostMapping("/save")
    public Map<String, Object> save(@RequestBody Map<String, Object> body) {
        Map<String, Object> result = new LinkedHashMap<>();
        try {
            String slotStr = (String) body.get("slot");

            PhotoEntryRequest request = new PhotoEntryRequest(
                    LocalDate.parse((String) body.get("date")),
                    (String) body.get("description"),
                    toBigDecimal(body.get("calories")),
                    toBigDecimal(body.get("protein")),
                    toBigDecimal(body.get("carbs")),
                    toBigDecimal(body.get("fat")),
                    toBigDecimal(body.get("fiber")),
                    slotStr != null && !slotStr.isEmpty() ? MealSlot.valueOf(slotStr) : null
            );

            foodLogService.addPhotoEntry(request);
            result.put("success", true);
        } catch (Exception e) {
            result.put("success", false);
            result.put("error", e.getMessage());
        }
        return result;
    }

    private BigDecimal toBigDecimal(Object val) {
        if (val == null) return null;
        return new BigDecimal(val.toString());
    }
}
