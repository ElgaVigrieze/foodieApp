package com.foodie.service;

import com.foodie.model.*;
import com.foodie.repository.MealPlanRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BudgetService {

    private final MealPlanRepository mealPlanRepository;

    /**
     * Get spending breakdown by product category for a specific week.
     */
    public BudgetReport getWeeklyReport(LocalDate weekStart) {
        LocalDate monday = weekStart.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        return mealPlanRepository.findByWeekStart(monday)
                .map(this::buildReport)
                .orElse(BudgetReport.empty(monday));
    }

    /**
     * Get overall spending across all meal plans that have at least one entry.
     */
    public BudgetReport getOverallReport() {
        List<MealPlan> allPlans = mealPlanRepository.findAll().stream()
                .filter(plan -> !plan.getEntries().isEmpty())
                .toList();
        if (allPlans.isEmpty()) {
            return BudgetReport.empty(null);
        }

        Map<ProductCategory, BigDecimal> categoryTotals = new LinkedHashMap<>();
        int weekCount = allPlans.size();

        for (MealPlan plan : allPlans) {
            Map<ProductCategory, BigDecimal> weekCosts = calculateCategoryBreakdown(plan);
            for (var entry : weekCosts.entrySet()) {
                categoryTotals.merge(entry.getKey(), entry.getValue(), BigDecimal::add);
            }
        }

        final BigDecimal grandTotal = categoryTotals.values().stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<CategorySpend> breakdown = categoryTotals.entrySet().stream()
                .map(e -> new CategorySpend(
                        e.getKey(),
                        e.getValue(),
                        grandTotal.compareTo(BigDecimal.ZERO) > 0
                                ? e.getValue().multiply(BigDecimal.valueOf(100)).divide(grandTotal, 1, RoundingMode.HALF_UP)
                                : BigDecimal.ZERO
                ))
                .sorted(Comparator.comparing(CategorySpend::amount).reversed())
                .toList();

        return new BudgetReport(null, grandTotal, breakdown, weekCount);
    }

    /**
     * Get list of all weeks that have meal plans with at least one entry.
     */
    public List<LocalDate> getAllWeeks() {
        return mealPlanRepository.findAll().stream()
                .filter(plan -> !plan.getEntries().isEmpty())
                .map(MealPlan::getWeekStart)
                .sorted()
                .toList();
    }

    private BudgetReport buildReport(MealPlan plan) {
        Map<ProductCategory, BigDecimal> categoryTotals = calculateCategoryBreakdown(plan);
        BigDecimal total = categoryTotals.values().stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<CategorySpend> breakdown = categoryTotals.entrySet().stream()
                .map(e -> new CategorySpend(
                        e.getKey(),
                        e.getValue(),
                        total.compareTo(BigDecimal.ZERO) > 0
                                ? e.getValue().multiply(BigDecimal.valueOf(100)).divide(total, 1, RoundingMode.HALF_UP)
                                : BigDecimal.ZERO
                ))
                .sorted(Comparator.comparing(CategorySpend::amount).reversed())
                .toList();

        return new BudgetReport(plan.getWeekStart(), total, breakdown, 1);
    }

    private Map<ProductCategory, BigDecimal> calculateCategoryBreakdown(MealPlan plan) {
        Map<ProductCategory, BigDecimal> categoryTotals = new LinkedHashMap<>();

        for (MealPlanEntry entry : plan.getEntries()) {
            int mealServings = entry.getMeal().getServings();
            int entryServings = entry.getServings() != null ? entry.getServings() : 1;
            BigDecimal ratio = BigDecimal.valueOf(entryServings)
                    .divide(BigDecimal.valueOf(mealServings), 4, RoundingMode.HALF_UP);

            for (MealIngredient ing : entry.getMeal().getIngredients()) {
                Product product = ing.getProduct();
                BigDecimal cost = ing.getQuantity().multiply(ratio).multiply(product.getPricePerUnit());
                ProductCategory category = product.getCategory() != null ? product.getCategory() : null;
                categoryTotals.merge(category, cost, BigDecimal::add);
            }
        }

        return categoryTotals;
    }

    // ── DTOs ───────────────────────────────────────────────────────────────

    public record BudgetReport(
            LocalDate weekStart,
            BigDecimal totalSpend,
            List<CategorySpend> breakdown,
            int weekCount
    ) {
        public static BudgetReport empty(LocalDate weekStart) {
            return new BudgetReport(weekStart, BigDecimal.ZERO, List.of(), 0);
        }

        public BigDecimal averagePerWeek() {
            if (weekCount == 0) return BigDecimal.ZERO;
            return totalSpend.divide(BigDecimal.valueOf(weekCount), 2, RoundingMode.HALF_UP);
        }
    }

    public record CategorySpend(
            ProductCategory category,
            BigDecimal amount,
            BigDecimal percentage
    ) {
        public String categoryLabel() {
            return category != null ? category.getLabel() : "Uncategorized";
        }
    }
}
