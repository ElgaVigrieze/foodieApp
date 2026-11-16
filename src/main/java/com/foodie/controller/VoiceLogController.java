package com.foodie.controller;

import com.foodie.controller.dto.VoiceParseRequest;
import com.foodie.controller.dto.VoiceParseResult;
import com.foodie.controller.dto.VoiceSubmitRequest;
import com.foodie.service.VoiceLogService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/voice")
@RequiredArgsConstructor
public class VoiceLogController {

    private final VoiceLogService voiceLogService;

    @PostMapping("/parse")
    public Map<String, Object> parse(@Valid @RequestBody VoiceParseRequest request) {
        return toResponseMap(voiceLogService.parse(request.text()));
    }

    @PostMapping("/submit")
    public Map<String, Object> submit(@Valid @RequestBody VoiceSubmitRequest request) {
        // Failures are translated to HTTP errors by ApiExceptionHandler
        voiceLogService.submit(request);
        return Map.of("success", true);
    }

    private Map<String, Object> toResponseMap(VoiceParseResult result) {
        Map<String, Object> map = new LinkedHashMap<>();

        map.put("parsed", result.parsed());

        if (!result.parsed()) {
            // "Nothing matched" is a normal outcome, not an error; the message tells the user why.
            if (result.error() != null) {
                map.put("error", result.error());
            }
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
