package com.foodie.service;

import com.foodie.model.Product;
import com.foodie.model.ProductCategory;
import com.foodie.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
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
        return productRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Product not found: " + id));
    }

    public Product save(Product product) {
        if (product.getHousehold() == null) {
            product.setHousehold(currentUserService.getCurrentHousehold());
        }
        nutritionLookupService.autoFillNutrition(product);
        return productRepository.save(product);
    }

    public void deleteById(Long id) {
        productRepository.deleteById(id);
    }
}
