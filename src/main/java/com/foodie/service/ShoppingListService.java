package com.foodie.service;

import com.foodie.model.MealPlan;
import com.foodie.model.Product;
import com.foodie.model.ShoppingListItem;
import com.foodie.repository.ShoppingListItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ShoppingListService {

    private final ShoppingListItemRepository repository;
    private final MealPlanService mealPlanService;

    /**
     * Get or generate shopping list for the given week.
     * If items already exist, returns them (preserving user edits).
     * If no items exist, generates from the meal plan.
     */
    @Transactional
    public List<ShoppingListItem> getOrGenerate(LocalDate weekStart, MealPlan plan) {
        List<ShoppingListItem> existing = repository.findByWeekStartOrderByProductNameAsc(weekStart);

        if (!existing.isEmpty()) {
            return existing;
        }

        return generateFromPlan(weekStart, plan);
    }

    /**
     * Re-generate from meal plan, preserving user adjustments where possible.
     */
    @Transactional
    public List<ShoppingListItem> regenerate(LocalDate weekStart, MealPlan plan) {
        List<ShoppingListItem> existing = repository.findByWeekStartOrderByProductNameAsc(weekStart);
        Map<Long, ShoppingListItem> existingByProductId = existing.stream()
                .collect(Collectors.toMap(i -> i.getProduct().getId(), Function.identity()));

        List<MealPlanService.ShoppingItem> calculated = mealPlanService.getShoppingList(plan);

        // Update existing or create new
        for (MealPlanService.ShoppingItem calc : calculated) {
            ShoppingListItem item = existingByProductId.get(calc.getProduct().getId());
            if (item != null) {
                // Update calculated quantity; keep adjusted if user changed it
                item.setCalculatedQuantity(calc.getTotalQuantity());
                // If user hadn't manually changed it, update adjusted too
                if (item.getAdjustedQuantity().compareTo(item.getCalculatedQuantity()) == 0
                        || item.getAdjustedQuantity().compareTo(calc.getTotalQuantity()) != 0) {
                    // Only auto-update if user hadn't specifically overridden
                }
                item.setCalculatedQuantity(calc.getTotalQuantity());
                existingByProductId.remove(calc.getProduct().getId());
            } else {
                // New item
                ShoppingListItem newItem = ShoppingListItem.builder()
                        .weekStart(weekStart)
                        .product(calc.getProduct())
                        .calculatedQuantity(calc.getTotalQuantity())
                        .adjustedQuantity(calc.getTotalQuantity())
                        .build();
                repository.save(newItem);
            }
        }

        // Remove items that are no longer in the plan
        for (ShoppingListItem orphan : existingByProductId.values()) {
            repository.delete(orphan);
        }

        repository.flush();
        return repository.findByWeekStartOrderByProductNameAsc(weekStart);
    }

    @Transactional
    public void updateQuantity(Long itemId, BigDecimal newQuantity) {
        ShoppingListItem item = repository.findById(itemId)
                .orElseThrow(() -> new IllegalArgumentException("Item not found: " + itemId));
        item.setAdjustedQuantity(newQuantity);
        repository.save(item);
    }

    @Transactional
    public void toggleChecked(Long itemId) {
        ShoppingListItem item = repository.findById(itemId)
                .orElseThrow(() -> new IllegalArgumentException("Item not found: " + itemId));
        item.setChecked(!item.isChecked());
        repository.save(item);
    }

    @Transactional
    public void addItem(LocalDate weekStart, Product product, BigDecimal quantity) {
        ShoppingListItem item = ShoppingListItem.builder()
                .weekStart(weekStart)
                .product(product)
                .calculatedQuantity(BigDecimal.ZERO) // manually added, not from plan
                .adjustedQuantity(quantity)
                .build();
        repository.save(item);
    }

    @Transactional
    public void removeItem(Long itemId) {
        repository.deleteById(itemId);
    }

    public BigDecimal getTotalCost(List<ShoppingListItem> items) {
        return items.stream()
                .filter(i -> !i.isChecked())
                .map(ShoppingListItem::getCost)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    // ── Private ────────────────────────────────────────────────────────────

    private List<ShoppingListItem> generateFromPlan(LocalDate weekStart, MealPlan plan) {
        List<MealPlanService.ShoppingItem> calculated = mealPlanService.getShoppingList(plan);

        List<ShoppingListItem> items = calculated.stream()
                .map(calc -> ShoppingListItem.builder()
                        .weekStart(weekStart)
                        .product(calc.getProduct())
                        .calculatedQuantity(calc.getTotalQuantity())
                        .adjustedQuantity(calc.getTotalQuantity())
                        .build())
                .toList();

        return repository.saveAll(items);
    }
}
