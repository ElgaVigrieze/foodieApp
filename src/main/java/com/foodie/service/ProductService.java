package com.foodie.service;

import com.foodie.controller.dto.BarcodeSaveRequest;
import com.foodie.exception.NotFoundException;
import com.foodie.model.ProductCategory;
import com.foodie.model.Unit;
import com.foodie.model.db.Product;
import com.foodie.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Products belong to a household and are shared by its members. Nobody else can see or use them.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ProductService {

    private final ProductRepository productRepository;
    private final NutritionLookupService nutritionLookupService;
    private final CurrentUserService currentUserService;

    public List<Product> findAll() {
        return productRepository.findByHouseholdIdOrderByNameAsc(currentUserService.requireCurrentHousehold().getId());
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
                .orElseThrow(() -> NotFoundException.of("Product", id));
        currentUserService.validateUserAccess(product);
        return product;
    }

    public Product save(Product product) {
        if (product.getId() == null) {
            product.setHousehold(currentUserService.requireCurrentHousehold());
        } else {
            currentUserService.validateUserAccess(product);
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
                .household(currentUserService.requireCurrentHousehold())
                .build();

        product = productRepository.save(product);
        log.info("Saved barcode product: {} (id={})", product.getName(), product.getId());
        return product;
    }

    public void deleteById(Long id) {
        Product product = findById(id); // validates access
        productRepository.delete(product);
    }
}
