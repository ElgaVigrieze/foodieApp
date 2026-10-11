package com.foodie.service;

import com.foodie.model.db.MealPlan;
import com.foodie.model.db.Product;
import com.foodie.model.db.ShoppingListItem;
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
     * Re-generate from the meal plan, preserving user adjustments where possible.
     */
    @Transactional
    public void regenerate(LocalDate weekStart, MealPlan plan) {
        List<ShoppingListItem> existing = repository.findByWeekStartOrderByProductNameAsc(weekStart);
        Map<Long, ShoppingListItem> existingByProductId = existing.stream()
                .collect(Collectors.toMap(i -> i.getProduct().getId(), Function.identity()));

        List<MealPlanService.ShoppingItem> calculated = mealPlanService.getShoppingList(plan);

        for (MealPlanService.ShoppingItem calc : calculated) {
            ShoppingListItem item = existingByProductId.remove(calc.getProduct().getId());
            if (item != null) {
                // Update calculated quantity
                BigDecimal oldCalculated = item.getCalculatedQuantity();
                item.setCalculatedQuantity(calc.getTotalQuantity());

                // If user hadn't manually adjusted, update adjusted quantity too
                if (item.getAdjustedQuantity().compareTo(oldCalculated) == 0) {
                    item.setAdjustedQuantity(calc.getTotalQuantity());
                }
            } else {
                // New item from plan
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
        repository.deleteAll(existingByProductId.values());
    }

    @Transactional
    public void updateQuantity(Long itemId, BigDecimal newQuantity) {
        ShoppingListItem item = findItemById(itemId);
        item.setAdjustedQuantity(newQuantity);
    }

    @Transactional
    public void toggleChecked(Long itemId) {
        ShoppingListItem item = findItemById(itemId);
        item.setChecked(!item.isChecked());
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
    public void updateComment(Long itemId, String comment) {
        ShoppingListItem item = findItemById(itemId);
        if (comment == null || comment.isBlank()) {
            item.setComment(null);
        } else {
            String trimmed = comment.trim();
            item.setComment(trimmed.length() > 50 ? trimmed.substring(0, 50) : trimmed);
        }
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

    private ShoppingListItem findItemById(Long itemId) {
        return repository.findById(itemId)
                .orElseThrow(() -> new IllegalArgumentException("Item not found: " + itemId));
    }

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