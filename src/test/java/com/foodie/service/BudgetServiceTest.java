package com.foodie.service;

import com.foodie.model.*;
import com.foodie.model.db.Meal;
import com.foodie.model.db.MealIngredient;
import com.foodie.model.db.MealPlan;
import com.foodie.model.db.MealPlanEntry;
import com.foodie.model.db.Product;
import com.foodie.repository.MealPlanRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BudgetServiceTest {

    @Mock
    private MealPlanRepository mealPlanRepository;

    @Mock
    private CurrentUserService currentUserService;

    @InjectMocks
    private BudgetService budgetService;

    private Product dairyProduct;
    private Product fruitProduct;
    private Product uncategorizedProduct;

    @BeforeEach
    void setUp() {
        // Create products with different categories
        dairyProduct = Product.builder()
                .id(1L)
                .name("Milk")
                .category(ProductCategory.DAIRY)
                .pricePerUnit(new BigDecimal("2.00"))
                .build();

        fruitProduct = Product.builder()
                .id(2L)
                .name("Apples")
                .category(ProductCategory.FRUIT_BERRIES)
                .pricePerUnit(new BigDecimal("3.00"))
                .build();

        uncategorizedProduct = Product.builder()
                .id(3L)
                .name("Mystery Item")
                .category(null)
                .pricePerUnit(new BigDecimal("5.00"))
                .build();
    }

    @Test
    void getWeeklyReport_shouldCalculateCategoryBreakdownCorrectly() {
        // Given: A meal with 2 servings containing dairy and fruit ingredients
        Meal meal = Meal.builder()
                .id(1L)
                .name("Test Meal")
                .servings(2)
                .build();

        MealIngredient dairyIngredient = MealIngredient.builder()
                .meal(meal)
                .product(dairyProduct)
                .quantity(new BigDecimal("1.0")) // 1 kg of milk at $2/kg = $2 for full recipe
                .build();

        MealIngredient fruitIngredient = MealIngredient.builder()
                .meal(meal)
                .product(fruitProduct)
                .quantity(new BigDecimal("0.5")) // 0.5 kg of apples at $3/kg = $1.50 for full recipe
                .build();

        meal.setIngredients(List.of(dairyIngredient, fruitIngredient));

        // Entry uses 1 serving out of 2 (ratio = 0.5)
        MealPlanEntry entry = MealPlanEntry.builder()
                .meal(meal)
                .servings(1)
                .build();

        MealPlan plan = MealPlan.builder()
                .weekStart(LocalDate.of(2024, 1, 1))
                .entries(List.of(entry))
                .build();

        when(currentUserService.getCurrentHouseholdId()).thenReturn(null);
        when(mealPlanRepository.findByWeekStart(any())).thenReturn(Optional.of(plan));

        // When
        BudgetService.BudgetReport report = budgetService.getWeeklyReport(LocalDate.of(2024, 1, 1));

        // Then
        // Dairy: 1.0 * 0.5 * 2.00 = $1.00
        // Fruit: 0.5 * 0.5 * 3.00 = $0.75
        // Total: $1.75
        assertThat(report.totalSpend()).isEqualByComparingTo(new BigDecimal("1.75"));
        assertThat(report.breakdown()).hasSize(2);

        var dairySpend = report.breakdown().stream()
                .filter(s -> s.category() == ProductCategory.DAIRY)
                .findFirst()
                .orElseThrow();
        assertThat(dairySpend.amount()).isEqualByComparingTo(new BigDecimal("1.00"));

        var fruitSpend = report.breakdown().stream()
                .filter(s -> s.category() == ProductCategory.FRUIT_BERRIES)
                .findFirst()
                .orElseThrow();
        assertThat(fruitSpend.amount()).isEqualByComparingTo(new BigDecimal("0.75"));
    }

    @Test
    void getWeeklyReport_shouldHandleNullServingsAsOne() {
        // Given: Entry with null servings (should default to 1)
        Meal meal = Meal.builder()
                .id(1L)
                .name("Test Meal")
                .servings(4)
                .build();

        MealIngredient ingredient = MealIngredient.builder()
                .meal(meal)
                .product(dairyProduct)
                .quantity(new BigDecimal("2.0")) // 2 kg at $2/kg = $4 for full recipe
                .build();

        meal.setIngredients(List.of(ingredient));

        MealPlanEntry entry = MealPlanEntry.builder()
                .meal(meal)
                .servings(null) // null should be treated as 1
                .build();

        MealPlan plan = MealPlan.builder()
                .weekStart(LocalDate.of(2024, 1, 1))
                .entries(List.of(entry))
                .build();

        when(currentUserService.getCurrentHouseholdId()).thenReturn(null);
        when(mealPlanRepository.findByWeekStart(any())).thenReturn(Optional.of(plan));

        // When
        BudgetService.BudgetReport report = budgetService.getWeeklyReport(LocalDate.of(2024, 1, 1));

        // Then: ratio = 1/4 = 0.25, cost = 2.0 * 0.25 * 2.00 = $1.00
        assertThat(report.totalSpend()).isEqualByComparingTo(new BigDecimal("1.00"));
    }

    @Test
    void getWeeklyReport_shouldHandleUncategorizedProducts() {
        // Given: A meal with an uncategorized product
        Meal meal = Meal.builder()
                .id(1L)
                .name("Test Meal")
                .servings(1)
                .build();

        MealIngredient ingredient = MealIngredient.builder()
                .meal(meal)
                .product(uncategorizedProduct)
                .quantity(new BigDecimal("1.0")) // 1 kg at $5/kg = $5
                .build();

        meal.setIngredients(List.of(ingredient));

        MealPlanEntry entry = MealPlanEntry.builder()
                .meal(meal)
                .servings(1)
                .build();

        MealPlan plan = MealPlan.builder()
                .weekStart(LocalDate.of(2024, 1, 1))
                .entries(List.of(entry))
                .build();

        when(currentUserService.getCurrentHouseholdId()).thenReturn(null);
        when(mealPlanRepository.findByWeekStart(any())).thenReturn(Optional.of(plan));

        // When
        BudgetService.BudgetReport report = budgetService.getWeeklyReport(LocalDate.of(2024, 1, 1));

        // Then
        assertThat(report.totalSpend()).isEqualByComparingTo(new BigDecimal("5.00"));
        assertThat(report.breakdown()).hasSize(1);
        assertThat(report.breakdown().get(0).category()).isNull();
        assertThat(report.breakdown().get(0).categoryLabel()).isEqualTo("Uncategorized");
    }

    @Test
    void getWeeklyReport_shouldAggregateMultipleEntriesWithSameCategory() {
        // Given: Two entries with ingredients in the same category
        Meal meal1 = Meal.builder().id(1L).name("Meal 1").servings(1).build();
        Meal meal2 = Meal.builder().id(2L).name("Meal 2").servings(1).build();

        MealIngredient ing1 = MealIngredient.builder()
                .meal(meal1)
                .product(dairyProduct)
                .quantity(new BigDecimal("1.0")) // $2.00
                .build();

        MealIngredient ing2 = MealIngredient.builder()
                .meal(meal2)
                .product(dairyProduct)
                .quantity(new BigDecimal("2.0")) // $4.00
                .build();

        meal1.setIngredients(List.of(ing1));
        meal2.setIngredients(List.of(ing2));

        MealPlanEntry entry1 = MealPlanEntry.builder().meal(meal1).servings(1).build();
        MealPlanEntry entry2 = MealPlanEntry.builder().meal(meal2).servings(1).build();

        MealPlan plan = MealPlan.builder()
                .weekStart(LocalDate.of(2024, 1, 1))
                .entries(List.of(entry1, entry2))
                .build();

        when(currentUserService.getCurrentHouseholdId()).thenReturn(null);
        when(mealPlanRepository.findByWeekStart(any())).thenReturn(Optional.of(plan));

        // When
        BudgetService.BudgetReport report = budgetService.getWeeklyReport(LocalDate.of(2024, 1, 1));

        // Then: Dairy should be aggregated: $2.00 + $4.00 = $6.00
        assertThat(report.totalSpend()).isEqualByComparingTo(new BigDecimal("6.00"));
        assertThat(report.breakdown()).hasSize(1);
        assertThat(report.breakdown().get(0).category()).isEqualTo(ProductCategory.DAIRY);
        assertThat(report.breakdown().get(0).amount()).isEqualByComparingTo(new BigDecimal("6.00"));
    }

    @Test
    void getWeeklyReport_shouldReturnEmptyReportWhenNoPlanExists() {
        // Given
        when(currentUserService.getCurrentHouseholdId()).thenReturn(null);
        when(mealPlanRepository.findByWeekStart(any())).thenReturn(Optional.empty());

        // When
        BudgetService.BudgetReport report = budgetService.getWeeklyReport(LocalDate.of(2024, 1, 1));

        // Then
        assertThat(report.totalSpend()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(report.breakdown()).isEmpty();
        assertThat(report.weekCount()).isZero();
    }

    @Test
    void getWeeklyReport_shouldCalculatePercentagesCorrectly() {
        // Given: Two categories with known amounts
        Meal meal = Meal.builder().id(1L).name("Test Meal").servings(1).build();

        // Dairy: $3.00, Vegetables: $1.00, Total: $4.00
        // Dairy %: 75%, Vegetables %: 25%
        Product dairyProduct2 = Product.builder()
                .id(10L)
                .name("Cheese")
                .category(ProductCategory.DAIRY)
                .pricePerUnit(new BigDecimal("3.00"))
                .build();

        Product vegetablesProduct = Product.builder()
                .id(11L)
                .name("Lettuce")
                .category(ProductCategory.VEGETABLES)
                .pricePerUnit(new BigDecimal("1.00"))
                .build();

        MealIngredient ing1 = MealIngredient.builder()
                .meal(meal)
                .product(dairyProduct2)
                .quantity(new BigDecimal("1.0"))
                .build();

        MealIngredient ing2 = MealIngredient.builder()
                .meal(meal)
                .product(vegetablesProduct)
                .quantity(new BigDecimal("1.0"))
                .build();

        meal.setIngredients(List.of(ing1, ing2));

        MealPlanEntry entry = MealPlanEntry.builder().meal(meal).servings(1).build();

        MealPlan plan = MealPlan.builder()
                .weekStart(LocalDate.of(2024, 1, 1))
                .entries(List.of(entry))
                .build();

        when(currentUserService.getCurrentHouseholdId()).thenReturn(null);
        when(mealPlanRepository.findByWeekStart(any())).thenReturn(Optional.of(plan));

        // When
        BudgetService.BudgetReport report = budgetService.getWeeklyReport(LocalDate.of(2024, 1, 1));

        // Then
        var dairySpend = report.breakdown().stream()
                .filter(s -> s.category() == ProductCategory.DAIRY)
                .findFirst()
                .orElseThrow();
        assertThat(dairySpend.percentage()).isEqualByComparingTo(new BigDecimal("75.0"));

        var vegetablesSpend = report.breakdown().stream()
                .filter(s -> s.category() == ProductCategory.VEGETABLES)
                .findFirst()
                .orElseThrow();
        assertThat(vegetablesSpend.percentage()).isEqualByComparingTo(new BigDecimal("25.0"));
    }
}