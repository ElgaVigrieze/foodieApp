package com.foodie.service;

import com.foodie.model.*;
import com.foodie.repository.MealRepository;
import com.foodie.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MealService {

    private final MealRepository mealRepository;
    private final ProductRepository productRepository;
    private final CurrentUserService currentUserService;

    public List<Meal> findAll() {
        AppUser user = currentUserService.getCurrentUser();
        if (user != null) {
            return mealRepository.findByOwnerIdOrOwnerIdIsNullOrderByNameAsc(user.getId());
        }
        return mealRepository.findAllByOrderByNameAsc();
    }

    public List<Meal> findByCategory(MealCategory category) {
        return mealRepository.findByCategoryOrderByNameAsc(category);
    }

    public Meal findById(Long id) {
        return mealRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Meal not found: " + id));
    }

    @Transactional
    public Meal save(Meal meal) {
        return mealRepository.save(meal);
    }

    @Transactional
    public Meal createMeal(String name, MealCategory category, int servings, String recipe,
                           List<Long> productIds, List<BigDecimal> quantities) {
        Meal meal = Meal.builder()
                .name(name)
                .category(category)
                .servings(servings)
                .recipe(recipe)
                .owner(currentUserService.getCurrentUser())
                .build();

        for (int i = 0; i < productIds.size(); i++) {
            if (productIds.get(i) != null && quantities.get(i) != null
                    && quantities.get(i).compareTo(BigDecimal.ZERO) > 0) {
                Product product = productRepository.findById(productIds.get(i))
                        .orElseThrow(() -> new IllegalArgumentException("Product not found"));
                MealIngredient ingredient = MealIngredient.builder()
                        .product(product)
                        .quantity(quantities.get(i))
                        .build();
                meal.addIngredient(ingredient);
            }
        }

        return mealRepository.save(meal);
    }

    @Transactional
    public Meal updateMeal(Long id, String name, MealCategory category, int servings, String recipe,
                           List<Long> productIds, List<BigDecimal> quantities) {
        Meal meal = findById(id);
        meal.setName(name);
        meal.setCategory(category);
        meal.setServings(servings);
        meal.setRecipe(recipe);

        // Clear existing ingredients and re-add
        meal.getIngredients().clear();

        for (int i = 0; i < productIds.size(); i++) {
            if (productIds.get(i) != null && quantities.get(i) != null
                    && quantities.get(i).compareTo(BigDecimal.ZERO) > 0) {
                Product product = productRepository.findById(productIds.get(i))
                        .orElseThrow(() -> new IllegalArgumentException("Product not found"));
                MealIngredient ingredient = MealIngredient.builder()
                        .product(product)
                        .quantity(quantities.get(i))
                        .build();
                meal.addIngredient(ingredient);
            }
        }

        return mealRepository.save(meal);
    }

    public void deleteById(Long id) {
        mealRepository.deleteById(id);
    }

    public List<Meal> findFavorites() {
        AppUser user = currentUserService.getCurrentUser();
        if (user != null) {
            return mealRepository.findByOwnerIdAndFavoriteTrueOrderByNameAsc(user.getId());
        }
        return mealRepository.findByFavoriteTrueOrderByNameAsc();
    }

    public Map<MealCategory, List<Meal>> findAllGroupedByCategory() {
        List<Meal> all = findAll();
        return all.stream()
                .filter(m -> !m.isFavorite())
                .collect(Collectors.groupingBy(Meal::getCategory, LinkedHashMap::new, Collectors.toList()));
    }

    @Transactional
    public void toggleFavorite(Long id) {
        Meal meal = findById(id);
        meal.setFavorite(!meal.isFavorite());
        mealRepository.save(meal);
    }
}
