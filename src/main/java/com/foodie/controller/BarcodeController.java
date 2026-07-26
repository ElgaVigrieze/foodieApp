package com.foodie.controller;

import com.foodie.model.Product;
import com.foodie.model.Unit;
import com.foodie.repository.ProductRepository;
import com.foodie.service.CurrentUserService;
import com.foodie.service.FoodLogService;
import com.foodie.service.NutritionLookupService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/barcode")
@RequiredArgsConstructor
public class BarcodeController {

    private final ProductRepository productRepository;
    private final NutritionLookupService nutritionLookupService;
    private final CurrentUserService currentUserService;
    private final FoodLogService foodLogService;

    /**
     * Lookup a barcode: first check local products, then Open Food Facts.
     */
    @PostMapping("/lookup")
    public Map<String, Object> lookup(@RequestBody Map<String, String> body) {
        Map<String, Object> result = new LinkedHashMap<>();
        String barcode = body.getOrDefault("barcode", "").trim();

        if (barcode.isEmpty()) {
            result.put("error", "No barcode provided");
            return result;
        }

        // 1. Check local products first
        Long hhId = currentUserService.getCurrentHouseholdId();
        Optional<Product> local = hhId != null
                ? productRepository.findByBarcodeAndHouseholdId(barcode, hhId)
                : productRepository.findByBarcode(barcode);

        if (local.isPresent()) {
            Product p = local.get();
            result.put("found", true);
            result.put("source", "local");
            result.put("productId", p.getId());
            result.put("name", p.getName());
            result.put("calories", p.getCalories());
            result.put("protein", p.getProtein());
            result.put("carbs", p.getCarbs());
            result.put("fat", p.getFat());
            result.put("fiber", p.getFiber());
            result.put("sugar", p.getSugar());
            result.put("unit", p.getUnit().name());
            return result;
        }

        // 2. Lookup on Open Food Facts
        Optional<NutritionLookupService.BarcodeResult> off = nutritionLookupService.lookupBarcode(barcode);
        if (off.isPresent()) {
            NutritionLookupService.BarcodeResult br = off.get();
            result.put("found", true);
            result.put("source", "openfoodfacts");
            result.put("barcode", barcode);
            result.put("name", br.name());
            result.put("quantity", br.quantity());
            result.put("calories", br.calories());
            result.put("protein", br.protein());
            result.put("carbs", br.carbs());
            result.put("fat", br.fat());
            result.put("fiber", br.fiber());
            result.put("sugar", br.sugar());
            return result;
        }

        // 3. Not found anywhere
        result.put("found", false);
        result.put("barcode", barcode);
        return result;
    }

    /**
     * Save a barcode product locally (creates a new Product in the household).
     */
    @PostMapping("/save-product")
    public Map<String, Object> saveProduct(@RequestBody Map<String, Object> body) {
        Map<String, Object> result = new LinkedHashMap<>();
        try {
            String barcode = (String) body.get("barcode");
            String name = (String) body.get("name");

            Product product = Product.builder()
                    .name(name)
                    .barcode(barcode)
                    .unit(Unit.KG)
                    .pricePerUnit(BigDecimal.ZERO)
                    .calories(toBD(body.get("calories")))
                    .protein(toBD(body.get("protein")))
                    .carbs(toBD(body.get("carbs")))
                    .fat(toBD(body.get("fat")))
                    .fiber(toBD(body.get("fiber")))
                    .sugar(toBD(body.get("sugar")))
                    .household(currentUserService.getCurrentHousehold())
                    .build();

            product = productRepository.save(product);
            result.put("success", true);
            result.put("productId", product.getId());
            result.put("name", product.getName());
        } catch (Exception e) {
            result.put("success", false);
            result.put("error", e.getMessage());
        }
        return result;
    }

    private BigDecimal toBD(Object val) {
        if (val == null) return null;
        return new BigDecimal(val.toString());
    }
}
