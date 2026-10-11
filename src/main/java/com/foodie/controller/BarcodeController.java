package com.foodie.controller;

import com.foodie.controller.dto.BarcodeLookupResult;
import com.foodie.controller.dto.BarcodeSaveRequest;
import com.foodie.model.db.Product;
import com.foodie.service.BarcodeService;
import com.foodie.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
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
    public Map<String, Object> lookup(@RequestBody Map<String, String> body) {
        String barcode = body.getOrDefault("barcode", "").trim();
        BarcodeLookupResult result = barcodeService.lookup(barcode);
        return toResponseMap(result);
    }

    /**
     * Save a barcode product locally (creates a new Product in the household).
     */
    @PostMapping("/save-product")
    public Map<String, Object> saveProduct(@RequestBody Map<String, Object> body) {
        Map<String, Object> response = new LinkedHashMap<>();
        try {
            BarcodeSaveRequest request = new BarcodeSaveRequest(
                    (String) body.get("barcode"),
                    (String) body.get("name"),
                    toBigDecimal(body.get("calories")),
                    toBigDecimal(body.get("protein")),
                    toBigDecimal(body.get("carbs")),
                    toBigDecimal(body.get("fat")),
                    toBigDecimal(body.get("fiber")),
                    toBigDecimal(body.get("sugar"))
            );

            Product product = productService.saveFromBarcode(request);
            response.put("success", true);
            response.put("productId", product.getId());
            response.put("name", product.getName());
        } catch (Exception e) {
            response.put("success", false);
            response.put("error", e.getMessage());
        }
        return response;
    }

    private Map<String, Object> toResponseMap(BarcodeLookupResult result) {
        Map<String, Object> map = new LinkedHashMap<>();

        if (result.error() != null) {
            map.put("error", result.error());
            return map;
        }

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

    private BigDecimal toBigDecimal(Object val) {
        if (val == null) return null;
        return new BigDecimal(val.toString());
    }
}