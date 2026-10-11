package com.foodie.service;

import com.foodie.client.OpenFoodFactsClient;
import com.foodie.client.OpenFoodFactsClient.BarcodeResponse;
import com.foodie.client.OpenFoodFactsClient.Nutriments;
import com.foodie.client.OpenFoodFactsClient.SearchResponse;
import com.foodie.controller.dto.NutritionBarcodeResult;
import com.foodie.controller.dto.NutritionInfo;
import com.foodie.model.db.Product;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * Service for looking up nutrition data.
 * Delegates HTTP calls to the OpenFoodFactsClient and handles business logic.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class NutritionLookupService {

    private final OpenFoodFactsClient openFoodFactsClient;

    /**
     * Search for nutrition info by product name.
     * Returns the first product with valid nutrition data.
     *
     * @param productName the product name to search for
     * @return nutrition info per 100g, or empty if not found
     */
    public Optional<NutritionInfo> lookup(String productName) {
        Optional<SearchResponse> response = openFoodFactsClient.searchByName(productName);

        if (response.isEmpty()) {
            return Optional.empty();
        }

        for (var product : response.get().getProducts()) {
            if (product.getNutriments() != null && product.getNutriments().hasData()) {
                NutritionInfo info = mapToNutritionInfo(product.getNutriments());
                log.info("Found nutrition for '{}': {} kcal, {}g protein", productName, info.calories(), info.protein());
                return Optional.of(info);
            }
        }

        log.info("No nutrition data in results for: {}", productName);
        return Optional.empty();
    }

    /**
     * Lookup a product by barcode.
     *
     * @param barcode the product barcode (EAN/UPC)
     * @return barcode result with name and nutrition, or empty if not found
     */
    public Optional<NutritionBarcodeResult> lookupBarcode(String barcode) {
        return openFoodFactsClient.getByBarcode(barcode)
                .map(this::mapToBarcodeResult);
    }

    /**
     * Fill in empty nutrition fields on a Product from Open Food Facts.
     * Only fills fields that are currently null.
     *
     * @param product the product to enrich
     */
    public void autoFillNutrition(Product product) {
        if (product.getName() == null || product.getName().isBlank()) {
            return;
        }
        if (hasAllNutritionFilled(product)) {
            return;
        }

        lookup(product.getName()).ifPresent(info -> {
            if (product.getCalories() == null) product.setCalories(info.calories());
            if (product.getProtein() == null) product.setProtein(info.protein());
            if (product.getCarbs() == null) product.setCarbs(info.carbs());
            if (product.getFat() == null) product.setFat(info.fat());
            if (product.getFiber() == null) product.setFiber(info.fiber());
            if (product.getSugar() == null) product.setSugar(info.sugar());
        });
    }

    private boolean hasAllNutritionFilled(Product product) {
        return product.getCalories() != null
                && product.getProtein() != null
                && product.getCarbs() != null
                && product.getFat() != null
                && product.getFiber() != null
                && product.getSugar() != null;
    }

    private NutritionInfo mapToNutritionInfo(Nutriments n) {
        return new NutritionInfo(
                toBigDecimal(n.getEnergyKcal100g()),
                toBigDecimal(n.getProteins100g()),
                toBigDecimal(n.getCarbohydrates100g()),
                toBigDecimal(n.getFat100g()),
                toBigDecimal(n.getFiber100g()),
                toBigDecimal(n.getSugars100g())
        );
    }

    private NutritionBarcodeResult mapToBarcodeResult(BarcodeResponse response) {
        return new NutritionBarcodeResult(
                response.getName(),
                response.getQuantity(),
                response.getCalories(),
                response.getProtein(),
                response.getCarbs(),
                response.getFat(),
                response.getFiber(),
                response.getSugar()
        );
    }

    private static BigDecimal toBigDecimal(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }
}