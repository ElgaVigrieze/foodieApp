package com.foodie.service;

import com.foodie.exception.NotFoundException;
import com.foodie.model.db.AppUser;
import com.foodie.model.db.Household;
import com.foodie.model.db.MealPlan;
import com.foodie.model.db.Product;
import com.foodie.model.db.ShoppingListItem;
import com.foodie.repository.ShoppingListItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ShoppingListService {

    private final ShoppingListItemRepository repository;
    private final MealPlanService mealPlanService;
    private final CurrentUserService currentUserService;

    /**
     * Get or generate shopping list for the given week.
     * If items already exist, returns them (preserving user edits).
     * If no items exist, generates from the meal plan.
     */
    @Transactional
    public List<ShoppingListItem> getOrGenerate(LocalDate weekStart, MealPlan plan) {
        List<ShoppingListItem> existing = itemsForWeek(weekStart);

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
        AppUser owner = currentUserService.getCurrentUser();
        Household household = currentUserService.requireCurrentHousehold();
        Map<Long, ShoppingListItem> existingByProductId = mergeDuplicates(itemsForWeek(weekStart));

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
                        .owner(owner)
                        .household(household)
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

    /**
     * Adds a product to the week's list. If the product is already on it (from the plan or added
     * earlier), its quantity goes up instead: the list never holds the same product twice.
     */
    @Transactional
    public void addItem(LocalDate weekStart, Product product, BigDecimal quantity) {
        var onList = itemsForWeek(weekStart).stream()
                .filter(i -> i.getProduct().getId().equals(product.getId()))
                .findFirst();
        if (onList.isPresent()) {
            ShoppingListItem item = onList.get();
            item.setAdjustedQuantity(item.getAdjustedQuantity().add(quantity));
            item.setChecked(false); // more is needed than was already bought
            return;
        }

        AppUser owner = currentUserService.getCurrentUser();
        Household household = currentUserService.requireCurrentHousehold();
        ShoppingListItem item = ShoppingListItem.builder()
                .owner(owner)
                .household(household)
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
        repository.delete(findItemById(itemId));
    }

    public BigDecimal getTotalCost(List<ShoppingListItem> items) {
        return items.stream()
                .filter(i -> !i.isChecked())
                .map(ShoppingListItem::getCost)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    // ── Private ────────────────────────────────────────────────────────────

    /** Loads an item the current user is allowed to see or change. */
    private ShoppingListItem findItemById(Long itemId) {
        ShoppingListItem item = repository.findById(itemId)
                .orElseThrow(() -> NotFoundException.of("Item", itemId));
        currentUserService.validateUserAccess(item);
        return item;
    }

    /**
     * Items by product. Lists saved before {@link #addItem} merged additions can hold the same product
     * more than once (e.g. once from the plan and once added by hand): those rows are folded into the
     * oldest one, adding up their quantities, and the others are deleted.
     */
    private Map<Long, ShoppingListItem> mergeDuplicates(List<ShoppingListItem> items) {
        Map<Long, List<ShoppingListItem>> byProduct = items.stream()
                .sorted(Comparator.comparing(ShoppingListItem::getId))
                .collect(Collectors.groupingBy(i -> i.getProduct().getId(), LinkedHashMap::new, Collectors.toList()));

        Map<Long, ShoppingListItem> merged = new LinkedHashMap<>();
        byProduct.forEach((productId, rows) -> {
            ShoppingListItem kept = rows.get(0);
            for (ShoppingListItem extra : rows.subList(1, rows.size())) {
                kept.setCalculatedQuantity(kept.getCalculatedQuantity().add(extra.getCalculatedQuantity()));
                kept.setAdjustedQuantity(kept.getAdjustedQuantity().add(extra.getAdjustedQuantity()));
                kept.setChecked(kept.isChecked() && extra.isChecked());
                if (kept.getComment() == null) {
                    kept.setComment(extra.getComment());
                }
                repository.delete(extra);
            }
            merged.put(productId, kept);
        });
        return merged;
    }

    /** The current user's household's list for a week. */
    private List<ShoppingListItem> itemsForWeek(LocalDate weekStart) {
        Long householdId = currentUserService.requireCurrentHousehold().getId();
        return repository.findByWeekStartAndHouseholdIdOrderByProductNameAsc(weekStart, householdId);
    }

    private List<ShoppingListItem> generateFromPlan(LocalDate weekStart, MealPlan plan) {
        AppUser owner = currentUserService.getCurrentUser();
        Household household = currentUserService.requireCurrentHousehold();
        List<MealPlanService.ShoppingItem> calculated = mealPlanService.getShoppingList(plan);

        List<ShoppingListItem> items = calculated.stream()
                .map(calc -> ShoppingListItem.builder()
                        .owner(owner)
                        .household(household)
                        .weekStart(weekStart)
                        .product(calc.getProduct())
                        .calculatedQuantity(calc.getTotalQuantity())
                        .adjustedQuantity(calc.getTotalQuantity())
                        .build())
                .toList();

        return repository.saveAll(items);
    }
}