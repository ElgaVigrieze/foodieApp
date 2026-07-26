package com.foodie.service;

import com.foodie.model.AppUser;
import com.foodie.model.FoodLog;
import com.foodie.model.Meal;
import com.foodie.repository.FoodLogRepository;
import com.foodie.repository.MealRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class FoodLogService {

    private final FoodLogRepository foodLogRepository;
    private final MealRepository mealRepository;
    private final com.foodie.repository.ProductRepository productRepository;
    private final CurrentUserService currentUserService;

    public List<FoodLog> findByDate(LocalDate date) {
        AppUser user = currentUserService.getCurrentUser();
        if (user != null) {
            return foodLogRepository.findByDateAndOwnerIdOrderByIdAsc(date, user.getId());
        }
        return foodLogRepository.findByDateOrderByIdAsc(date);
    }

    public FoodLog addEntry(LocalDate date, Long mealId, BigDecimal servingsConsumed, com.foodie.model.MealSlot slot) {
        Meal meal = mealRepository.findById(mealId)
                .orElseThrow(() -> new IllegalArgumentException("Meal not found: " + mealId));
        FoodLog entry = FoodLog.builder()
                .date(date)
                .meal(meal)
                .servingsConsumed(servingsConsumed)
                .slot(slot)
                .owner(currentUserService.getCurrentUser())
                .household(currentUserService.getCurrentHousehold())
                .build();
        return foodLogRepository.save(entry);
    }

    
    public FoodLog addProductEntry(LocalDate date, Long productId, BigDecimal quantity, com.foodie.model.MealSlot slot) {
        com.foodie.model.Product product = productRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("Product not found: " + productId));
        FoodLog entry = FoodLog.builder()
                .date(date)
                .product(product)
                .productQuantity(quantity)
                .servingsConsumed(java.math.BigDecimal.ONE)
                .slot(slot)
                .owner(currentUserService.getCurrentUser())
                .household(currentUserService.getCurrentHousehold())
                .build();
        return foodLogRepository.save(entry);
    }

    public void updateCaloriesSpent(LocalDate date, BigDecimal caloriesSpent) {
        // Store on the first entry of the day (or create a placeholder)
        List<FoodLog> entries = findByDate(date);
        if (!entries.isEmpty()) {
            entries.getFirst().setCaloriesSpent(caloriesSpent);
            foodLogRepository.save(entries.getFirst());
        }
    }

    public BigDecimal getCaloriesSpent(LocalDate date) {
        List<FoodLog> entries = findByDate(date);
        return entries.stream()
                .map(FoodLog::getCaloriesSpent)
                .filter(java.util.Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    public BigDecimal getDailyCost(LocalDate date) {
        List<FoodLog> entries = findByDate(date);
        return entries.stream()
                .map(e -> e.getCost())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    
    public void updateServings(Long id, BigDecimal servings) {
        FoodLog entry = foodLogRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Entry not found: " + id));
        entry.setServingsConsumed(servings);
        foodLogRepository.save(entry);
    }
    public void deleteEntry(Long id) {
        foodLogRepository.deleteById(id);
    }

    public DailyTotals getDailyTotals(LocalDate date) {
        List<FoodLog> entries = findByDate(date);
        BigDecimal calories = BigDecimal.ZERO;
        BigDecimal protein = BigDecimal.ZERO;
        BigDecimal carbs = BigDecimal.ZERO;
        BigDecimal fat = BigDecimal.ZERO;
        BigDecimal fiber = BigDecimal.ZERO;
        BigDecimal sugar = BigDecimal.ZERO;

        for (FoodLog entry : entries) {
            calories = calories.add(entry.getCalories());
            protein = protein.add(entry.getProtein());
            carbs = carbs.add(entry.getCarbs());
            fat = fat.add(entry.getFat());
            fiber = fiber.add(entry.getFiber());
            sugar = sugar.add(entry.getSugar());
        }

        return new DailyTotals(calories, protein, carbs, fat, fiber, sugar,
                carbs.subtract(fiber));
    }

    
    public java.util.Map<com.foodie.model.MealSlot, DailyTotals> getSlotTotals(LocalDate date) {
        List<FoodLog> entries = findByDate(date);
        java.util.Map<com.foodie.model.MealSlot, DailyTotals> result = new java.util.LinkedHashMap<>();
        for (com.foodie.model.MealSlot slot : com.foodie.model.MealSlot.values()) {
            BigDecimal cal = BigDecimal.ZERO, pro = BigDecimal.ZERO, carb = BigDecimal.ZERO;
            BigDecimal fat = BigDecimal.ZERO, fib = BigDecimal.ZERO, sug = BigDecimal.ZERO;
            for (FoodLog e : entries) {
                if (e.getSlot() == slot) {
                    cal = cal.add(e.getCalories());
                    pro = pro.add(e.getProtein());
                    carb = carb.add(e.getCarbs());
                    fat = fat.add(e.getFat());
                    fib = fib.add(e.getFiber());
                    sug = sug.add(e.getSugar());
                }
            }
            result.put(slot, new DailyTotals(cal, pro, carb, fat, fib, sug, carb.subtract(fib)));
        }
        return result;
    }
    public record DailyTotals(
            BigDecimal calories, BigDecimal protein, BigDecimal carbs,
            BigDecimal fat, BigDecimal fiber, BigDecimal sugar, BigDecimal netCarbs
    ) {}
}
