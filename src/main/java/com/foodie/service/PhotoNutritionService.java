package com.foodie.service;

import com.foodie.client.CloudflareAiClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Service for analyzing food photos and estimating nutrition.
 * Uses Cloudflare Workers AI vision model.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class PhotoNutritionService {

    private final CloudflareAiClient cloudflareAiClient;

    public boolean isAvailable() {
        return cloudflareAiClient.isAvailable();
    }

    /**
     * Analyze a food photo and return estimated nutrition using Cloudflare Workers AI.
     */
    public NutritionEstimate analyzePhoto(byte[] imageBytes, String mimeType) {
        if (!isAvailable()) {
            return new NutritionEstimate("Photo analysis not configured. Set CLOUDFLARE_ACCOUNT_ID and CLOUDFLARE_API_TOKEN.", null, null, null, null, null, null);
        }

        String base64Image = Base64.getEncoder().encodeToString(imageBytes);

        String prompt = "Analyze this food photo. Estimate the total nutritional content of everything visible. " +
                "Respond ONLY in this exact format:\\nFOOD: <brief description>\\nCALORIES: <number>\\nPROTEIN: <number>g\\nCARBS: <number>g\\nFAT: <number>g\\nFIBER: <number>g\\nWEIGHT: <number>g";

        return cloudflareAiClient.callVisionModel(
                        CloudflareAiClient.VISION_MODEL,
                        prompt,
                        base64Image,
                        mimeType,
                        512)
                .map(this::parseResponse)
                .orElseGet(() -> new NutritionEstimate("Error: Failed to get AI response", null, null, null, null, null, null));
    }

    private NutritionEstimate parseResponse(String text) {
        log.info("Parsed AI text: {}", text);

        String food = extractValue(text, "FOOD:\\s*(.+)");
        BigDecimal calories = extractNumber(text, "CALORIES:\\s*(\\d+\\.?\\d*)");
        BigDecimal protein = extractNumber(text, "PROTEIN:\\s*(\\d+\\.?\\d*)");
        BigDecimal carbs = extractNumber(text, "CARBS:\\s*(\\d+\\.?\\d*)");
        BigDecimal fat = extractNumber(text, "FAT:\\s*(\\d+\\.?\\d*)");
        BigDecimal fiber = extractNumber(text, "FIBER:\\s*(\\d+\\.?\\d*)");
        BigDecimal weight = extractNumber(text, "WEIGHT:\\s*(\\d+\\.?\\d*)");

        return new NutritionEstimate(food, calories, protein, carbs, fat, fiber, weight);
    }

    private String extractValue(String text, String regex) {
        Matcher m = Pattern.compile(regex).matcher(text);
        return m.find() ? m.group(1).trim() : null;
    }

    private BigDecimal extractNumber(String text, String regex) {
        Matcher m = Pattern.compile(regex).matcher(text);
        return m.find() ? new BigDecimal(m.group(1)) : null;
    }

    public record NutritionEstimate(
            String description,
            BigDecimal calories,
            BigDecimal protein,
            BigDecimal carbs,
            BigDecimal fat,
            BigDecimal fiber,
            BigDecimal estimatedWeightGrams
    ) {
        public boolean isValid() {
            return calories != null && description != null && !description.startsWith("Error") && !description.startsWith("Could not");
        }
    }
}
