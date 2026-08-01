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
        Long hhId = currentUserService.getCurrentHouseholdId();
        if (hhId != null) {
            return mealRepository.findByHouseholdIdAndArchivedFalseOrderByNameAsc(hhId);
        }
        return mealRepository.findByArchivedFalseOrderByNameAsc();
    }

    public List<Meal> findArchived() {
        Long hhId = currentUserService.getCurrentHouseholdId();
        if (hhId != null) {
            return mealRepository.findByHouseholdIdAndArchivedTrueOrderByNameAsc(hhId);
        }
        return mealRepository.findByArchivedTrueOrderByNameAsc();
    }

    public List<Meal> findFavorites() {
        Long hhId = currentUserService.getCurrentHouseholdId();
        if (hhId != null) {
            return mealRepository.findByHouseholdIdAndArchivedFalseAndFavoriteTrueOrderByNameAsc(hhId);
        }
        return mealRepository.findByArchivedFalseAndFavoriteTrueOrderByNameAsc();
    }

    public Map<MealCategory, List<Meal>> findAllGroupedByCategory() {
        List<Meal> all = findAll();
        return all.stream()
                .filter(m -> !m.isFavorite())
                .collect(Collectors.groupingBy(Meal::getCategory, LinkedHashMap::new, Collectors.toList()));
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
    public Meal createMeal(String name, MealCategory category, int servings, String recipe, String recipeUrl,
                           List<Long> productIds, List<BigDecimal> quantities) {
        Meal meal = Meal.builder()
                .name(name)
                .category(category)
                .servings(servings)
                .recipe(recipe)
                .recipeUrl(recipeUrl)
                .owner(currentUserService.getCurrentUser())
                .household(currentUserService.getCurrentHousehold())
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
    public Meal updateMeal(Long id, String name, MealCategory category, int servings, String recipe, String recipeUrl,
                           List<Long> productIds, List<BigDecimal> quantities) {
        Meal meal = findById(id);
        meal.setName(name);
        meal.setCategory(category);
        meal.setServings(servings);
        meal.setRecipe(recipe);
        meal.setRecipeUrl(recipeUrl);
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

    @Transactional
    public void toggleFavorite(Long id) {
        Meal meal = findById(id);
        meal.setFavorite(!meal.isFavorite());
        mealRepository.save(meal);
    }

    @Transactional
    public void toggleArchive(Long id) {
        Meal meal = findById(id);
        meal.setArchived(!meal.isArchived());
        // Remove from favorites when archiving
        if (meal.isArchived()) meal.setFavorite(false);
        mealRepository.save(meal);
    }

    public List<Meal> findByCategory(MealCategory category) {
        return mealRepository.findByCategoryOrderByNameAsc(category);
    }
}
