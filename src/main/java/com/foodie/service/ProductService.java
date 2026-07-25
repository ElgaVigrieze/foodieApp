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

    public List<Product> findAll() {
        return productRepository.findAllByOrderByNameAsc();
    }

    /**
     * Returns products grouped by category, ordered by category enum order then name.
     * Products without a category are grouped under null key.
     */
    public Map<ProductCategory, List<Product>> findAllGroupedByCategory() {
        List<Product> all = productRepository.findAllByOrderByNameAsc();
        // Group by category, maintaining enum order
        Map<ProductCategory, List<Product>> grouped = all.stream()
                .filter(p -> p.getCategory() != null)
                .collect(Collectors.groupingBy(Product::getCategory, LinkedHashMap::new, Collectors.toList()));

        // Add uncategorized at the end
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

    /**
     * Save a product. If nutrition fields are empty, auto-fill from Open Food Facts.
     */
    public Product save(Product product) {
        nutritionLookupService.autoFillNutrition(product);
        return productRepository.save(product);
    }

    public void deleteById(Long id) {
        productRepository.deleteById(id);
    }
}
