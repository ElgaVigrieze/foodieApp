package com.foodie.service;

import com.foodie.exception.BadRequestException;
import com.foodie.controller.dto.BarcodeLookupResult;
import com.foodie.controller.dto.NutritionBarcodeResult;
import com.foodie.model.db.Product;
import com.foodie.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Service for barcode-related operations.
 * Handles local product lookup and integration with external nutrition APIs.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class BarcodeService {

    private final ProductRepository productRepository;
    private final NutritionLookupService nutritionLookupService;
    private final CurrentUserService currentUserService;

    /**
     * Look up a barcode: first check local products, then Open Food Facts.
     *
     * @param barcode the barcode to lookup
     * @return lookup result indicating source and product data
     * @throws IllegalArgumentException if the barcode is missing or blank
     */
    public BarcodeLookupResult lookup(String barcode) {
        if (barcode == null || barcode.isBlank()) {
            throw new BadRequestException("No barcode provided");
        }

        // 1. The household's own products
        Long householdId = currentUserService.requireCurrentHousehold().getId();
        Optional<Product> localProduct = productRepository.findFirstByBarcodeAndHouseholdId(barcode, householdId);

        if (localProduct.isPresent()) {
            return BarcodeLookupResult.fromLocalProduct(localProduct.get());
        }

        // 2. Lookup on Open Food Facts
        Optional<NutritionBarcodeResult> externalResult = nutritionLookupService.lookupBarcode(barcode);
        return externalResult.map(
                nutritionBarcodeResult ->
                        BarcodeLookupResult.fromExternalSource(barcode, nutritionBarcodeResult))
                .orElseGet(() -> BarcodeLookupResult.notFound(barcode));
    }
}
