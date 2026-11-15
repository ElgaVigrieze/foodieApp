package com.foodie.controller.dto;

import com.foodie.model.db.Product;

import java.math.BigDecimal;

/**
 * Result of a barcode lookup.
 */
public record BarcodeLookupResult(
        boolean found,
        String source,
        String barcode,
        Long productId,
        String name,
        String quantity,
        BigDecimal calories,
        BigDecimal protein,
        BigDecimal carbs,
        BigDecimal fat,
        BigDecimal fiber,
        BigDecimal sugar,
        String unit
) {
    public static BarcodeLookupResult notFound(String barcode) {
        return new BarcodeLookupResult(false, null, barcode, null, null, null, null, null, null, null, null, null, null);
    }

    public static BarcodeLookupResult fromLocalProduct(Product p) {
        return new BarcodeLookupResult(
                true,
                "local",
                p.getBarcode(),
                p.getId(),
                p.getName(),
                null,
                p.getCalories(),
                p.getProtein(),
                p.getCarbs(),
                p.getFat(),
                p.getFiber(),
                p.getSugar(),
                p.getUnit().name()
        );
    }

    public static BarcodeLookupResult fromExternalSource(String barcode, NutritionBarcodeResult br) {
        return new BarcodeLookupResult(
                true,
                "openfoodfacts",
                barcode,
                null,
                br.name(),
                br.quantity(),
                br.calories(),
                br.protein(),
                br.carbs(),
                br.fat(),
                br.fiber(),
                br.sugar(),
                null
        );
    }
}
