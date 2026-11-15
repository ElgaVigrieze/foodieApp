package com.foodie.controller;

import com.foodie.controller.dto.PhotoEntryRequest;
import com.foodie.model.MealSlot;
import com.foodie.service.FoodLogService;
import com.foodie.service.PhotoNutritionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.UncheckedIOException;
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

        // Failures are translated to HTTP errors by ApiExceptionHandler
        if (!photoNutritionService.isAvailable()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Photo analysis not configured. Set CLOUDFLARE_ACCOUNT_ID and CLOUDFLARE_API_TOKEN.");
        }

        if (image.isEmpty()) {
            throw new IllegalArgumentException("No image provided");
        }

        byte[] bytes;
        try {
            bytes = image.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read uploaded image", e);
        }
        String mimeType = image.getContentType() != null ? image.getContentType() : "image/jpeg";

        PhotoNutritionService.NutritionEstimate estimate = photoNutritionService.analyzePhoto(bytes, mimeType);

        if (!estimate.isValid()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, estimate.description());
        }

        result.put("success", true);
        result.put("description", estimate.description());
        result.put("calories", estimate.calories());
        result.put("protein", estimate.protein());
        result.put("carbs", estimate.carbs());
        result.put("fat", estimate.fat());
        result.put("fiber", estimate.fiber());
        result.put("weightGrams", estimate.estimatedWeightGrams());
        return result;
    }

    @PostMapping("/save")
    public Map<String, Object> save(@RequestBody Map<String, Object> body) {
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
        return Map.of("success", true);
    }

    private BigDecimal toBigDecimal(Object val) {
        if (val == null) return null;
        return new BigDecimal(val.toString());
    }
}
