package com.foodie.service;

import com.foodie.model.*;
import com.foodie.repository.MealPlanRepository;
import com.foodie.repository.MealRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MealPlanService {

    private final MealPlanRepository mealPlanRepository;
    private final MealRepository mealRepository;
    private final CurrentUserService currentUserService;

    /**
     * Get or create a meal plan for the week containing the given date.
     */
    @Transactional
    public MealPlan getOrCreateForWeek(LocalDate date) {
        LocalDate monday = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        Long hhId = currentUserService.getCurrentHouseholdId();

        if (hhId != null) {
            return mealPlanRepository.findByWeekStartAndHouseholdId(monday, hhId)
                    .orElseGet(() -> mealPlanRepository.save(
                            MealPlan.builder()
                                    .name("Week of " + monday)
                                    .weekStart(monday)
                                    .owner(currentUserService.getCurrentUser())
                                    .household(currentUserService.getCurrentHousehold())
                                    .build()
                    ));
        }

        return mealPlanRepository.findByWeekStart(monday)
                .orElseGet(() -> mealPlanRepository.save(
                        MealPlan.builder()
                                .name("Week of " + monday)
                                .weekStart(monday)
                                .build()
                ));
    }

    @Transactional
    public void addEntry(LocalDate weekStart, DayOfWeek day, MealSlot slot, Long mealId, Integer servings) {
        MealPlan plan = getOrCreateForWeek(weekStart);
        Meal meal = mealRepository.findById(mealId)
                .orElseThrow(() -> new IllegalArgumentException("Meal not found: " + mealId));

        MealPlanEntry entry = MealPlanEntry.builder()
                .dayOfWeek(day)
                .slot(slot)
                .meal(meal)
                .servings(servings != null ? servings : 1)
                .build();
        plan.addEntry(entry);
        mealPlanRepository.save(plan);
    }

    @Transactional
    public void removeEntry(Long planId, Long entryId) {
        MealPlan plan = mealPlanRepository.findById(planId)
                .orElseThrow(() -> new IllegalArgumentException("Plan not found: " + planId));
        if (plan.isFrozen()) {
            throw new IllegalStateException("Cannot remove entries from a frozen plan.");
        }
        plan.getEntries().removeIf(e -> e.getId().equals(entryId));
        mealPlanRepository.save(plan);
    }

    @Transactional
    public void moveEntry(Long planId, Long entryId, DayOfWeek newDay, MealSlot newSlot) {
        MealPlan plan = mealPlanRepository.findById(planId)
                .orElseThrow(() -> new IllegalArgumentException("Plan not found: " + planId));
        plan.getEntries().stream()
                .filter(e -> e.getId().equals(entryId))
                .findFirst()
                .ifPresent(entry -> {
                    entry.setDayOfWeek(newDay);
                    entry.setSlot(newSlot);
                });
        mealPlanRepository.save(plan);
    }

    @Transactional
    public void updateEntryServings(Long planId, Long entryId, Integer servings) {
        MealPlan plan = mealPlanRepository.findById(planId)
                .orElseThrow(() -> new IllegalArgumentException("Plan not found: " + planId));
        plan.getEntries().stream()
                .filter(e -> e.getId().equals(entryId))
                .findFirst()
                .ifPresent(entry -> entry.setServings(servings != null && servings > 0 ? servings : 1));
        mealPlanRepository.save(plan);
    }

    @Transactional
    public void copyEntry(Long planId, Long entryId, DayOfWeek newDay, MealSlot newSlot) {
        MealPlan plan = mealPlanRepository.findById(planId)
                .orElseThrow(() -> new IllegalArgumentException("Plan not found: " + planId));
        plan.getEntries().stream()
                .filter(e -> e.getId().equals(entryId))
                .findFirst()
                .ifPresent(original -> {
                    MealPlanEntry copy = MealPlanEntry.builder()
                            .dayOfWeek(newDay)
                            .slot(newSlot)
                            .meal(original.getMeal())
                            .servings(original.getServings())
                            .build();
                    plan.addEntry(copy);
                });
        mealPlanRepository.save(plan);
    }

    @Transactional
    public void toggleFreeze(Long planId) {
        MealPlan plan = mealPlanRepository.findById(planId)
                .orElseThrow(() -> new IllegalArgumentException("Plan not found: " + planId));
        plan.setFrozen(!plan.isFrozen());
        mealPlanRepository.save(plan);
    }

    @Transactional
    public void togglePrepared(Long planId, Long entryId) {
        MealPlan plan = mealPlanRepository.findById(planId)
                .orElseThrow(() -> new IllegalArgumentException("Plan not found: " + planId));
        plan.getEntries().stream()
                .filter(e -> e.getId().equals(entryId))
                .findFirst()
                .ifPresent(entry -> entry.setPrepared(!entry.isPrepared()));
        mealPlanRepository.save(plan);
    }

    /**
     * Build a shopping list from the meal plan â€” aggregates all ingredient quantities.
     */
    public List<ShoppingItem> getShoppingList(MealPlan plan) {
        Map<Long, ShoppingItem> items = new LinkedHashMap<>();

        for (MealPlanEntry entry : plan.getEntries()) {
            int mealServings = entry.getMeal().getServings();
            int entryServings = entry.getServings() != null ? entry.getServings() : 1;
            BigDecimal ratio = BigDecimal.valueOf(entryServings)
                    .divide(BigDecimal.valueOf(mealServings), 4, java.math.RoundingMode.HALF_UP);

            for (MealIngredient ing : entry.getMeal().getIngredients()) {
                Product product = ing.getProduct();
                BigDecimal quantityNeeded = ing.getQuantity().multiply(ratio);
                items.computeIfAbsent(product.getId(), id -> new ShoppingItem(product))
                        .addQuantity(quantityNeeded);
            }
        }

        return new ArrayList<>(items.values());
    }

    public BigDecimal getShoppingListTotalCost(List<ShoppingItem> items) {
        return items.stream()
                .map(ShoppingItem::getCost)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Calculate total calories per day for a meal plan.
     */
    public Map<DayOfWeek, BigDecimal> getDailyCalories(MealPlan plan) {
        Map<DayOfWeek, BigDecimal> dailyCals = new LinkedHashMap<>();
        for (DayOfWeek day : DayOfWeek.values()) {
            dailyCals.put(day, BigDecimal.ZERO);
        }

        for (MealPlanEntry entry : plan.getEntries()) {
            BigDecimal calsForEntry = entry.getMeal().getCaloriesPerServing()
                    .multiply(BigDecimal.valueOf(entry.getServings() != null ? entry.getServings() : 1));
            dailyCals.merge(entry.getDayOfWeek(), calsForEntry, BigDecimal::add);
        }

        return dailyCals;
    }

    /**
     * Represents a product + aggregated quantity in the shopping list.
     */
    public static class ShoppingItem {
        private final Product product;
        private BigDecimal totalQuantity = BigDecimal.ZERO;

        public ShoppingItem(Product product) {
            this.product = product;
        }

        public void addQuantity(BigDecimal qty) {
            this.totalQuantity = this.totalQuantity.add(qty);
        }

        public Product getProduct() { return product; }
        public BigDecimal getTotalQuantity() { return totalQuantity; }

        public BigDecimal getCost() {
            return product.getPricePerUnit().multiply(totalQuantity);
        }
    }
}

