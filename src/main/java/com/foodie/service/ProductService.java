package com.foodie.service;

import com.foodie.controller.dto.BarcodeSaveRequest;
import com.foodie.model.db.Product;
import com.foodie.model.ProductCategory;
import com.foodie.model.Unit;
import com.foodie.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProductService {

    private final ProductRepository productRepository;
    private final NutritionLookupService nutritionLookupService;
    private final CurrentUserService currentUserService;

    public List<Product> findAll() {
        Long hhId = currentUserService.getCurrentHouseholdId();
        if (hhId != null) {
            return productRepository.findByHouseholdIdOrHouseholdIdIsNullOrderByNameAsc(hhId);
        }
        return productRepository.findAllByOrderByNameAsc();
    }

    public Map<ProductCategory, List<Product>> findAllGroupedByCategory() {
        List<Product> all = findAll();
        Map<ProductCategory, List<Product>> grouped = all.stream()
                .filter(p -> p.getCategory() != null)
                .collect(Collectors.groupingBy(Product::getCategory, LinkedHashMap::new, Collectors.toList()));
        List<Product> uncategorized = all.stream()
                .filter(p -> p.getCategory() == null)
                .toList();
        if (!uncategorized.isEmpty()) {
            grouped.put(null, uncategorized);
        }
        return grouped;
    }

    public Product findById(Long id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Product not found: " + id));
        validateProductAccess(product);
        return product;
    }

    public Product save(Product product) {
        if (product.getHousehold() == null) {
            product.setHousehold(currentUserService.getCurrentHousehold());
        }
        nutritionLookupService.autoFillNutrition(product);
        return productRepository.save(product);
    }

    /**
     * Save a product from barcode scan data.
     *
     * @param request the barcode product data
     * @return the saved product
     */
    public Product saveFromBarcode(BarcodeSaveRequest request) {
        Product product = Product.builder()
                .name(request.name())
                .barcode(request.barcode())
                .unit(Unit.KG)
                .pricePerUnit(BigDecimal.ZERO)
                .calories(request.calories())
                .protein(request.protein())
                .carbs(request.carbs())
                .fat(request.fat())
                .fiber(request.fiber())
                .sugar(request.sugar())
                .household(currentUserService.getCurrentHousehold())
                .build();

        product = productRepository.save(product);
        log.info("Saved barcode product: {} (id={})", product.getName(), product.getId());
        return product;
    }

    public void deleteById(Long id) {
        Product product = findById(id); // validates access
        productRepository.delete(product);
    }

    /**
     * Validates that the current user has access to the product.
     * Products are accessible if:
     * - They have no household (global/shared products)
     * - They belong to the user's household
     */
    private void validateProductAccess(Product product) {
        if (product.getHousehold() == null) {
            return; // Global products are accessible to all
        }
        currentUserService.validateUserAccess(product);
    }
}