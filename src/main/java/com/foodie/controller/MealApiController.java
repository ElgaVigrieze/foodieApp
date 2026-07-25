package com.foodie.controller;

import com.foodie.model.Meal;
import com.foodie.model.MealIngredient;
import com.foodie.service.MealService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/meals")
@RequiredArgsConstructor
public class MealApiController {

    private final MealService mealService;

    @GetMapping("/{id}/debug")
    public Map<String, Object> debug(@PathVariable Long id) {
        Meal meal = mealService.findById(id);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("name", meal.getName());
        result.put("servings", meal.getServings());
        result.put("totalCost", meal.getTotalCost());
        result.put("costPerServing", meal.getCostPerServing());
        result.put("caloriesPerServing", meal.getCaloriesPerServing());

        List<Map<String, Object>> ingredients = new ArrayList<>();
        for (MealIngredient ing : meal.getIngredients()) {
            Map<String, Object> ingMap = new LinkedHashMap<>();
            ingMap.put("product", ing.getProduct().getName());
            ingMap.put("unit", ing.getProduct().getUnit());
            ingMap.put("quantity", ing.getQuantity());
            ingMap.put("pricePerUnit", ing.getProduct().getPricePerUnit());
            ingMap.put("cost", ing.getCost());
            ingMap.put("caloriesPer100g", ing.getProduct().getCalories());
            ingMap.put("totalCalories", ing.getNutrientAmount(com.foodie.model.NutrientType.CALORIES));
            ingredients.add(ingMap);
        }
        result.put("ingredients", ingredients);
        return result;
    }
}
