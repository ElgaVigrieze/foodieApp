package com.foodie.service;

import com.foodie.model.AppUser;
import com.foodie.model.FoodLog;
import com.foodie.model.Meal;
import com.foodie.repository.FoodLogRepository;
import com.foodie.repository.MealRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;

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

    
    
    public void updateProductQuantity(Long id, BigDecimal quantity) {
        FoodLog entry = foodLogRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Entry not found: " + id));
        entry.setProductQuantity(quantity);
        foodLogRepository.save(entry);
    }
    public void updateServings(Long id, BigDecimal servings) {
        FoodLog entry = foodLogRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Entry not found: " + id));
        entry.setServingsConsumed(servings);
        foodLogRepository.save(entry);
    }
    @Transactional
    public void scalePhotoEntry(Long id, BigDecimal scalePct) {
        FoodLog entry = foodLogRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Entry not found: " + id));
        if (entry.getDirectCalories() == null) return; // not a photo entry

        BigDecimal factor = scalePct.divide(BigDecimal.valueOf(100), 4, java.math.RoundingMode.HALF_UP);
        entry.setDirectCalories(entry.getDirectCalories().multiply(factor).setScale(1, java.math.RoundingMode.HALF_UP));
        if (entry.getDirectProtein()  != null) entry.setDirectProtein(entry.getDirectProtein().multiply(factor).setScale(1, java.math.RoundingMode.HALF_UP));
        if (entry.getDirectCarbs()    != null) entry.setDirectCarbs(entry.getDirectCarbs().multiply(factor).setScale(1, java.math.RoundingMode.HALF_UP));
        if (entry.getDirectFat()      != null) entry.setDirectFat(entry.getDirectFat().multiply(factor).setScale(1, java.math.RoundingMode.HALF_UP));
        if (entry.getDirectFiber()    != null) entry.setDirectFiber(entry.getDirectFiber().multiply(factor).setScale(1, java.math.RoundingMode.HALF_UP));
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

    public record DayStatus(boolean hasEntries, Boolean allTargetsMet,
                             BigDecimal calories, BigDecimal netCalories, BigDecimal protein, BigDecimal carbs,
                             BigDecimal fat, BigDecimal fiber, BigDecimal cost) {}

    /** Convenience constructor for days with no entries. */
    private static DayStatus noEntries() {
        return new DayStatus(false, null,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
    }

    /**
     * Returns a map of day-of-month -> DayStatus for a given month.
     * DayStatus.hasEntries = true if any food was logged
     * DayStatus.allTargetsMet = null if no targets set OR no entries, true if all set targets met, false otherwise
     */
    public Map<Integer, DayStatus> getMonthlyCalendarData(YearMonth month, AppUser user) {
        Map<Integer, DayStatus> result = new LinkedHashMap<>();
        LocalDate start = month.atDay(1);
        LocalDate end = month.atEndOfMonth();

        List<FoodLog> entries;
        if (user != null) {
            entries = foodLogRepository.findByDateBetweenAndOwnerIdOrderByDateAscIdAsc(start, end, user.getId());
        } else {
            entries = foodLogRepository.findByDateBetweenOrderByDateAscIdAsc(start, end);
        }

        // Group entries by day
        Map<Integer, List<FoodLog>> byDay = new LinkedHashMap<>();
        for (FoodLog e : entries) {
            int day = e.getDate().getDayOfMonth();
            byDay.computeIfAbsent(day, k -> new java.util.ArrayList<>()).add(e);
        }

        boolean hasAnyTarget = user != null && (
                user.getTargetCalories() != null || user.getTargetFiber() != null ||
                user.getTargetCarbs() != null || user.getTargetFat() != null ||
                user.getTargetProtein() != null || user.getTargetCost() != null);

        for (int day = 1; day <= month.lengthOfMonth(); day++) {
            List<FoodLog> dayEntries = byDay.get(day);
            boolean hasEntries = dayEntries != null && !dayEntries.isEmpty();

            if (!hasEntries) {
                result.put(day, noEntries());
                continue;
            }

            // Compute totals for the day
            BigDecimal cal = BigDecimal.ZERO, pro = BigDecimal.ZERO;
            BigDecimal carb = BigDecimal.ZERO, fat = BigDecimal.ZERO, fib = BigDecimal.ZERO;
            BigDecimal cost = BigDecimal.ZERO, burned = BigDecimal.ZERO;
            for (FoodLog e : dayEntries) {
                cal = cal.add(e.getCalories());
                pro = pro.add(e.getProtein());
                carb = carb.add(e.getCarbs());
                fat = fat.add(e.getFat());
                fib = fib.add(e.getFiber());
                cost = cost.add(e.getCost());
                if (e.getCaloriesSpent() != null) burned = burned.add(e.getCaloriesSpent());
            }
            BigDecimal netCal = cal.subtract(burned);

            if (!hasAnyTarget) {
                result.put(day, new DayStatus(true, null, cal, netCal, pro, carb, fat, fib, cost));
                continue;
            }

            boolean allMet = true;
            if (user.getTargetCalories() != null) allMet &= netCal.compareTo(user.getTargetCalories()) < 0;
            if (user.getTargetFiber() != null)    allMet &= fib.compareTo(user.getTargetFiber()) >= 0;
            if (user.getTargetCarbs() != null)    allMet &= carb.compareTo(user.getTargetCarbs()) < 0;
            if (user.getTargetFat() != null)      allMet &= fat.compareTo(user.getTargetFat()) >= 0;
            if (user.getTargetProtein() != null)  allMet &= pro.compareTo(user.getTargetProtein()) >= 0;
            if (user.getTargetCost() != null)     allMet &= cost.compareTo(user.getTargetCost()) < 0;

            result.put(day, new DayStatus(true, allMet, cal, netCal, pro, carb, fat, fib, cost));
        }

        return result;
    }
}