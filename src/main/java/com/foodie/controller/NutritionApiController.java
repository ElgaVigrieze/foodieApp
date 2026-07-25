package com.foodie.controller;

import com.foodie.service.NutritionLookupService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/nutrition")
@RequiredArgsConstructor
public class NutritionApiController {

    private final NutritionLookupService nutritionLookupService;

    /**
     * Lookup nutrition info by product name.
     * Returns 200 with data or 404 if not found.
     */
    @GetMapping("/lookup")
    public ResponseEntity<NutritionLookupService.NutritionInfo> lookup(@RequestParam String name) {
        return nutritionLookupService.lookup(name)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
