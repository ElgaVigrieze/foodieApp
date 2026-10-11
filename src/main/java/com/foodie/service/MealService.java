package com.foodie.service;

import com.foodie.model.*;
import com.foodie.model.db.Meal;
import com.foodie.model.db.MealIngredient;
import com.foodie.model.db.Product;
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

    /**
     * Verifies the current user can access/modify the given meal.
     * @throws SecurityException if the user doesn't have access
     */
    private Meal findByIdAndVerifyAccess(Long id) {
        Meal meal = mealRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Meal not found: " + id));

        currentUserService.validateUserAccess(meal);

        return meal;
    }


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
        return findByIdAndVerifyAccess(id);
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

        mapIngredients(productIds, quantities, meal);
        return mealRepository.save(meal);
    }

    @Transactional
    public void updateMeal(Long id, String name, MealCategory category, int servings, String recipe, String recipeUrl,
                           List<Long> productIds, List<BigDecimal> quantities) {
        Meal meal = findById(id);
        meal.setName(name);
        meal.setCategory(category);
        meal.setServings(servings);
        meal.setRecipe(recipe);
        meal.setRecipeUrl(recipeUrl);
        meal.getIngredients().clear();

        mapIngredients(productIds, quantities, meal);
        mealRepository.save(meal);
    }

    public void deleteById(Long id) {
        findByIdAndVerifyAccess(id);  // Verify access before deleting
        mealRepository.deleteById(id);
    }

    @Transactional
    public void toggleFavorite(Long id) {
        Meal meal = findByIdAndVerifyAccess(id);
        meal.setFavorite(!meal.isFavorite());
        mealRepository.save(meal);
    }

    @Transactional
    public void toggleArchive(Long id) {
        Meal meal = findByIdAndVerifyAccess(id);
        meal.setArchived(!meal.isArchived());
        // Remove from favorites when archiving
        if (meal.isArchived()) meal.setFavorite(false);
        mealRepository.save(meal);
    }

    private void mapIngredients(List<Long> productIds, List<BigDecimal> quantities, Meal meal) {
        // Filter valid entries and collect IDs to fetch
        List<Long> validIds = new java.util.ArrayList<>();
        for (int i = 0; i < productIds.size(); i++) {
            if (productIds.get(i) != null && quantities.get(i) != null
                    && quantities.get(i).compareTo(BigDecimal.ZERO) > 0) {
                validIds.add(productIds.get(i));
            }
        }

        if (validIds.isEmpty()) return;

        // Batch fetch all products in one query
        Map<Long, Product> productsById = productRepository.findAllById(validIds).stream()
                .collect(Collectors.toMap(Product::getId, p -> p));

        // Build ingredients
        for (int i = 0; i < productIds.size(); i++) {
            Long productId = productIds.get(i);
            BigDecimal quantity = quantities.get(i);

            if (productId != null && quantity != null && quantity.compareTo(BigDecimal.ZERO) > 0) {
                Product product = productsById.get(productId);
                if (product == null) {
                    throw new IllegalArgumentException("Product not found: " + productId);
                }
                MealIngredient ingredient = MealIngredient.builder()
                        .product(product)
                        .quantity(quantity)
                        .build();
                meal.addIngredient(ingredient);
            }
        }
    }
}