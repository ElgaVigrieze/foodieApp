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
    private final CurrentUserService currentUserService;

    public List<FoodLog> findByDate(LocalDate date) {
        AppUser user = currentUserService.getCurrentUser();
        if (user != null) {
            return foodLogRepository.findByDateAndOwnerIdOrderByIdAsc(date, user.getId());
        }
        return foodLogRepository.findByDateOrderByIdAsc(date);
    }

    public FoodLog addEntry(LocalDate date, Long mealId, BigDecimal servingsConsumed) {
        Meal meal = mealRepository.findById(mealId)
                .orElseThrow(() -> new IllegalArgumentException("Meal not found: " + mealId));
        FoodLog entry = FoodLog.builder()
                .date(date)
                .meal(meal)
                .servingsConsumed(servingsConsumed)
                .owner(currentUserService.getCurrentUser())
                .build();
        return foodLogRepository.save(entry);
    }

    public void deleteEntry(Long id) {
        foodLogRepository.deleteById(id);
    }

    /**
     * Compute daily totals for a given date.
     */
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

    public record DailyTotals(
            BigDecimal calories,
            BigDecimal protein,
            BigDecimal carbs,
            BigDecimal fat,
            BigDecimal fiber,
            BigDecimal sugar,
            BigDecimal netCarbs
    ) {}
}
