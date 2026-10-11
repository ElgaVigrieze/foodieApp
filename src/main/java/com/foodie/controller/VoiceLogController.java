package com.foodie.controller;

import com.foodie.controller.dto.VoiceParseResult;
import com.foodie.controller.dto.VoiceSubmitRequest;
import com.foodie.controller.dto.VoiceSubmitResult;
import com.foodie.model.MealSlot;
import com.foodie.service.VoiceLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/voice")
@RequiredArgsConstructor
public class VoiceLogController {

    private final VoiceLogService voiceLogService;

    @PostMapping("/parse")
    public Map<String, Object> parse(@RequestBody Map<String, String> body) {
        String text = body.getOrDefault("text", "");
        VoiceParseResult result = voiceLogService.parse(text);
        return toResponseMap(result);
    }

    @PostMapping("/submit")
    public Map<String, Object> submit(@RequestBody Map<String, Object> body) {
        Map<String, Object> response = new LinkedHashMap<>();
        try {
            VoiceSubmitRequest request = new VoiceSubmitRequest(
                    (String) body.get("type"),
                    LocalDate.parse((String) body.get("date")),
                    body.get("slot") != null ? MealSlot.valueOf((String) body.get("slot")) : null,
                    body.get("mealId") != null ? Long.valueOf(body.get("mealId").toString()) : null,
                    body.get("servings") != null ? new BigDecimal(body.get("servings").toString()) : null,
                    body.get("productId") != null ? Long.valueOf(body.get("productId").toString()) : null,
                    body.get("quantity") != null ? new BigDecimal(body.get("quantity").toString()) : null
            );

            VoiceSubmitResult result = voiceLogService.submit(request);
            response.put("success", result.success());
            if (result.error() != null) {
                response.put("error", result.error());
            }
        } catch (Exception e) {
            response.put("success", false);
            response.put("error", e.getMessage());
        }
        return response;
    }

    private Map<String, Object> toResponseMap(VoiceParseResult result) {
        Map<String, Object> map = new LinkedHashMap<>();

        if (result.error() != null && !result.parsed()) {
            map.put("error", result.error());
            map.put("parsed", false);
            return map;
        }

        map.put("parsed", result.parsed());

        if (!result.parsed()) {
            return map;
        }

        map.put("type", result.type());
        if (result.mealId() != null) map.put("mealId", result.mealId());
        if (result.mealName() != null) map.put("mealName", result.mealName());
        if (result.productId() != null) map.put("productId", result.productId());
        if (result.productName() != null) map.put("productName", result.productName());
        if (result.servings() != null) map.put("servings", result.servings());
        if (result.quantity() != null) map.put("quantity", result.quantity());
        if (result.slot() != null) map.put("slot", result.slot());

        return map;
    }
}
