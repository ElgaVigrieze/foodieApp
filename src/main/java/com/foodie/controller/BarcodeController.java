package com.foodie.controller;

import com.foodie.controller.dto.BarcodeLookupRequest;
import com.foodie.controller.dto.BarcodeLookupResult;
import com.foodie.controller.dto.BarcodeSaveRequest;
import com.foodie.model.db.Product;
import com.foodie.service.BarcodeService;
import com.foodie.service.ProductService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/barcode")
@RequiredArgsConstructor
public class BarcodeController {

    private final BarcodeService barcodeService;
    private final ProductService productService;

    /**
     * Lookup a barcode: first check local products, then Open Food Facts.
     */
    @PostMapping("/lookup")
    public Map<String, Object> lookup(@Valid @RequestBody BarcodeLookupRequest request) {
        return toResponseMap(barcodeService.lookup(request.barcode()));
    }

    /**
     * Save a barcode product locally (creates a new Product in the household).
     */
    @PostMapping("/save-product")
    public Map<String, Object> saveProduct(@Valid @RequestBody BarcodeSaveRequest request) {
        // Failures are translated to HTTP errors by ApiExceptionHandler
        Product product = productService.saveFromBarcode(request);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("productId", product.getId());
        response.put("name", product.getName());
        return response;
    }

    private Map<String, Object> toResponseMap(BarcodeLookupResult result) {
        Map<String, Object> map = new LinkedHashMap<>();

        map.put("found", result.found());

        if (!result.found()) {
            map.put("barcode", result.barcode());
            return map;
        }

        map.put("source", result.source());
        if (result.barcode() != null) map.put("barcode", result.barcode());
        if (result.productId() != null) map.put("productId", result.productId());
        if (result.name() != null) map.put("name", result.name());
        if (result.quantity() != null) map.put("quantity", result.quantity());
        if (result.calories() != null) map.put("calories", result.calories());
        if (result.protein() != null) map.put("protein", result.protein());
        if (result.carbs() != null) map.put("carbs", result.carbs());
        if (result.fat() != null) map.put("fat", result.fat());
        if (result.fiber() != null) map.put("fiber", result.fiber());
        if (result.sugar() != null) map.put("sugar", result.sugar());
        if (result.unit() != null) map.put("unit", result.unit());

        return map;
    }
}