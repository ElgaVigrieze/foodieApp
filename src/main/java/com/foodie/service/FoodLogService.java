package com.foodie.service;

import com.foodie.controller.dto.DailyTotals;
import com.foodie.controller.dto.DayStatus;
import com.foodie.controller.dto.PhotoEntryRequest;
import com.foodie.model.db.AppUser;
import com.foodie.model.db.FoodLog;
import com.foodie.model.db.Meal;
import com.foodie.model.MealSlot;
import com.foodie.model.db.Product;
import com.foodie.repository.FoodLogRepository;
import com.foodie.repository.MealRepository;
import com.foodie.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;

@Service
@RequiredArgsConstructor
public class FoodLogService {

    private final FoodLogRepository foodLogRepository;
    private final MealRepository mealRepository;
    private final ProductRepository productRepository;
    private final CurrentUserService currentUserService;

    /**
     * Verifies the current user owns the given food log entry.
     * @throws SecurityException if the user doesn't own the entry
     */
    private FoodLog findByIdAndVerifyOwnership(Long id) {
        FoodLog entry = foodLogRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Entry not found: " + id));

        currentUserService.validateUserAccess(entry);

        return entry;
    }

    public List<FoodLog> findByDate(LocalDate date) {
        AppUser user = currentUserService.getCurrentUser();
        if (user != null) {
            return foodLogRepository.findByDateAndOwnerIdOrderByIdAsc(date, user.getId());
        }
        return foodLogRepository.findByDateOrderByIdAsc(date);
    }

    public void addEntry(LocalDate date, Long mealId, BigDecimal servingsConsumed, MealSlot slot) {
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
        foodLogRepository.save(entry);
    }

    public void addProductEntry(LocalDate date, Long productId, BigDecimal quantity, MealSlot slot) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("Product not found: " + productId));
        FoodLog entry = FoodLog.builder()
                .date(date)
                .product(product)
                .productQuantity(quantity)
                .servingsConsumed(BigDecimal.ONE)
                .slot(slot)
                .owner(currentUserService.getCurrentUser())
                .household(currentUserService.getCurrentHousehold())
                .build();
        foodLogRepository.save(entry);
    }

    /**
     * Add a food log entry from photo analysis with direct nutrition values.
     *
     * @param request the photo entry data
     */
    public void addPhotoEntry(PhotoEntryRequest request) {
        FoodLog entry = FoodLog.builder()
                .date(request.date())
                .photoDescription(request.description())
                .directCalories(request.calories())
                .directProtein(request.protein())
                .directCarbs(request.carbs())
                .directFat(request.fat())
                .directFiber(request.fiber())
                .servingsConsumed(BigDecimal.ONE)
                .slot(request.slot())
                .owner(currentUserService.getCurrentUser())
                .household(currentUserService.getCurrentHousehold())
                .build();
        foodLogRepository.save(entry);
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
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    public BigDecimal getDailyCost(LocalDate date) {
        List<FoodLog> entries = findByDate(date);
        return entries.stream()
                .map(FoodLog::getCost)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public void updateProductQuantity(Long id, BigDecimal quantity) {
        FoodLog entry = findByIdAndVerifyOwnership(id);
        entry.setProductQuantity(quantity);
        foodLogRepository.save(entry);
    }

    public void updateServings(Long id, BigDecimal servings) {
        FoodLog entry = findByIdAndVerifyOwnership(id);
        entry.setServingsConsumed(servings);
        foodLogRepository.save(entry);
    }

    public void deleteEntry(Long id) {
        findByIdAndVerifyOwnership(id);  // Verify ownership before deleting
        foodLogRepository.deleteById(id);
    }

    public DailyTotals getDailyTotals(LocalDate date) {
        NutritionTotals totals = sumEntries(findByDate(date));
        return totals.toDailyTotals();
    }

    public Map<MealSlot, DailyTotals> getSlotTotals(LocalDate date) {
        List<FoodLog> entries = findByDate(date);
        Map<MealSlot, DailyTotals> result = new LinkedHashMap<>();

        for (MealSlot slot : MealSlot.values()) {
            List<FoodLog> slotEntries = entries.stream()
                    .filter(e -> e.getSlot() == slot)
                    .toList();
            result.put(slot, sumEntries(slotEntries).toDailyTotals());
        }
        return result;
    }

    /**
     * Returns a map of day-of-month -> DayStatus for a given month.
     * DayStatus.hasEntries = true if any food was logged
     * DayStatus.allTargetsMet = null if no targets set OR no entries, true if all set targets met, false otherwise
     */
    public Map<Integer, DayStatus> getMonthlyCalendarData(YearMonth month, AppUser user) {
        List<FoodLog> entries = fetchEntriesForMonth(month, user);
        Map<Integer, List<FoodLog>> byDay = groupEntriesByDay(entries);
        boolean hasAnyTarget = hasAnyTarget(user);

        Map<Integer, DayStatus> result = new LinkedHashMap<>();
        for (int day = 1; day <= month.lengthOfMonth(); day++) {
            result.put(day, buildDayStatus(byDay.get(day), user, hasAnyTarget));
        }
        return result;
    }

    private List<FoodLog> fetchEntriesForMonth(YearMonth month, AppUser user) {
        LocalDate start = month.atDay(1);
        LocalDate end = month.atEndOfMonth();
        if (user != null) {
            return foodLogRepository.findByDateBetweenAndOwnerIdOrderByDateAscIdAsc(start, end, user.getId());
        }
        return foodLogRepository.findByDateBetweenOrderByDateAscIdAsc(start, end);
    }

    private Map<Integer, List<FoodLog>> groupEntriesByDay(List<FoodLog> entries) {
        Map<Integer, List<FoodLog>> byDay = new LinkedHashMap<>();
        for (FoodLog e : entries) {
            byDay.computeIfAbsent(e.getDate().getDayOfMonth(), k -> new ArrayList<>()).add(e);
        }
        return byDay;
    }

    private boolean hasAnyTarget(AppUser user) {
        return user != null && (
                user.getTargetCalories() != null || user.getTargetFiber() != null ||
                user.getTargetCarbs() != null || user.getTargetFat() != null ||
                user.getTargetProtein() != null || user.getTargetCost() != null);
    }

    private DayStatus buildDayStatus(List<FoodLog> dayEntries, AppUser user, boolean hasAnyTarget) {
        if (dayEntries == null || dayEntries.isEmpty()) {
            return DayStatus.noEntries();
        }

        NutritionTotals totals = sumEntries(dayEntries);
        BigDecimal netCalories = totals.calories.subtract(totals.burned);

        if (!hasAnyTarget) {
            return new DayStatus(true, null, totals.calories, netCalories,
                    totals.protein, totals.carbs, totals.fat, totals.fiber, totals.cost);
        }

        boolean allMet = checkAllTargetsMet(totals, netCalories, user);
        return new DayStatus(true, allMet, totals.calories, netCalories,
                totals.protein, totals.carbs, totals.fat, totals.fiber, totals.cost);
    }

    /** Aggregates nutrition values from a list of food log entries. */
    private NutritionTotals sumEntries(List<FoodLog> entries) {
        BigDecimal cal = BigDecimal.ZERO, pro = BigDecimal.ZERO, carb = BigDecimal.ZERO;
        BigDecimal fat = BigDecimal.ZERO, fib = BigDecimal.ZERO, sug = BigDecimal.ZERO;
        BigDecimal cost = BigDecimal.ZERO, burned = BigDecimal.ZERO;

        for (FoodLog e : entries) {
            cal = cal.add(e.getCalories());
            pro = pro.add(e.getProtein());
            carb = carb.add(e.getCarbs());
            fat = fat.add(e.getFat());
            fib = fib.add(e.getFiber());
            sug = sug.add(e.getSugar());
            cost = cost.add(e.getCost());
            if (e.getCaloriesSpent() != null) {
                burned = burned.add(e.getCaloriesSpent());
            }
        }

        return new NutritionTotals(cal, pro, carb, fat, fib, sug, cost, burned);
    }

    private boolean checkAllTargetsMet(NutritionTotals totals, BigDecimal netCalories, AppUser user) {
        return isUnderLimit(netCalories, user.getTargetCalories())
            && meetsMinimum(totals.fiber, user.getTargetFiber())
            && isUnderLimit(totals.carbs, user.getTargetCarbs())
            && meetsMinimum(totals.fat, user.getTargetFat())
            && meetsMinimum(totals.protein, user.getTargetProtein())
            && isUnderLimit(totals.cost, user.getTargetCost());
    }

    /** Returns true if target is null OR actual < target (staying under limit). */
    private boolean isUnderLimit(BigDecimal actual, BigDecimal target) {
        return target == null || actual.compareTo(target) < 0;
    }

    /** Returns true if target is null OR actual >= target (meeting minimum). */
    private boolean meetsMinimum(BigDecimal actual, BigDecimal target) {
        return target == null || actual.compareTo(target) >= 0;
    }

    /** Holds aggregated nutrition totals for a list of entries. */
    private record NutritionTotals(
            BigDecimal calories,
            BigDecimal protein,
            BigDecimal carbs,
            BigDecimal fat,
            BigDecimal fiber,
            BigDecimal sugar,
            BigDecimal cost,
            BigDecimal burned
    ) {
        DailyTotals toDailyTotals() {
            return new DailyTotals(calories, protein, carbs, fat, fiber, sugar, carbs.subtract(fiber));
        }
    }
}