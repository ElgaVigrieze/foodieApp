package com.foodie.service;

import com.foodie.controller.dto.DayStatus;
import com.foodie.model.db.AppUser;
import com.foodie.model.db.FoodLog;
import com.foodie.model.MealSlot;
import com.foodie.repository.FoodLogRepository;
import com.foodie.repository.MealRepository;
import com.foodie.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link FoodLogService}, specifically the getMonthlyCalendarData method.
 */
@ExtendWith(MockitoExtension.class)
class FoodLogServiceTest {

    @Mock
    private FoodLogRepository foodLogRepository;

    @Mock
    private MealRepository mealRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private CurrentUserService currentUserService;

    @InjectMocks
    private FoodLogService foodLogService;

    private AppUser userWithTargets;
    private AppUser userWithoutTargets;

    @BeforeEach
    void setUp() {
        userWithTargets = AppUser.builder()
                .id(1L)
                .name("Test User")
                .targetCalories(new BigDecimal("2000"))
                .targetProtein(new BigDecimal("100"))
                .targetFiber(new BigDecimal("25"))
                .targetCarbs(new BigDecimal("250"))
                .targetFat(new BigDecimal("65"))
                .targetCost(new BigDecimal("15"))
                .build();

        userWithoutTargets = AppUser.builder()
                .id(2L)
                .name("User No Targets")
                .build();
    }

    private FoodLog createFoodLogWithDirectValues(LocalDate date, BigDecimal calories, BigDecimal protein,
                                                   BigDecimal carbs, BigDecimal fat, BigDecimal fiber,
                                                   BigDecimal caloriesSpent) {
        return FoodLog.builder()
                .id(1L)
                .date(date)
                .slot(MealSlot.LUNCH)
                .servingsConsumed(BigDecimal.ONE)
                .directCalories(calories)
                .directProtein(protein)
                .directCarbs(carbs)
                .directFat(fat)
                .directFiber(fiber)
                .caloriesSpent(caloriesSpent)
                .build();
    }

    @Nested
    @DisplayName("getMonthlyCalendarData")
    class GetMonthlyCalendarData {

        @Test
        @DisplayName("Should return DayStatus.noEntries() for days without entries")
        void shouldReturnNoEntriesForEmptyDays() {
            YearMonth month = YearMonth.of(2024, 1);

            when(foodLogRepository.findByDateBetweenAndOwnerIdOrderByDateAscIdAsc(
                    any(LocalDate.class), any(LocalDate.class), eq(1L)))
                    .thenReturn(List.of());

            Map<Integer, DayStatus> result = foodLogService.getMonthlyCalendarData(month, userWithTargets);

            assertThat(result).hasSize(31); // January has 31 days
            assertThat(result.get(1).hasEntries()).isFalse();
            assertThat(result.get(1).allTargetsMet()).isNull();
            assertThat(result.get(15).hasEntries()).isFalse();
        }

        @Test
        @DisplayName("Should calculate totals correctly for day with entries")
        void shouldCalculateTotalsForDayWithEntries() {
            YearMonth month = YearMonth.of(2024, 1);
            LocalDate day15 = LocalDate.of(2024, 1, 15);

            FoodLog entry1 = createFoodLogWithDirectValues(day15,
                    new BigDecimal("500"), new BigDecimal("30"),
                    new BigDecimal("60"), new BigDecimal("20"), new BigDecimal("10"), null);
            FoodLog entry2 = createFoodLogWithDirectValues(day15,
                    new BigDecimal("300"), new BigDecimal("20"),
                    new BigDecimal("40"), new BigDecimal("10"), new BigDecimal("5"), null);

            when(foodLogRepository.findByDateBetweenAndOwnerIdOrderByDateAscIdAsc(
                    any(LocalDate.class), any(LocalDate.class), eq(1L)))
                    .thenReturn(List.of(entry1, entry2));

            Map<Integer, DayStatus> result = foodLogService.getMonthlyCalendarData(month, userWithTargets);

            DayStatus day15Status = result.get(15);
            assertThat(day15Status.hasEntries()).isTrue();
            assertThat(day15Status.calories()).isEqualByComparingTo(new BigDecimal("800"));
            assertThat(day15Status.protein()).isEqualByComparingTo(new BigDecimal("50"));
            assertThat(day15Status.carbs()).isEqualByComparingTo(new BigDecimal("100"));
            assertThat(day15Status.fat()).isEqualByComparingTo(new BigDecimal("30"));
            assertThat(day15Status.fiber()).isEqualByComparingTo(new BigDecimal("15"));
        }

        @Test
        @DisplayName("Should calculate netCalories correctly with caloriesSpent")
        void shouldCalculateNetCaloriesWithCaloriesSpent() {
            YearMonth month = YearMonth.of(2024, 1);
            LocalDate day10 = LocalDate.of(2024, 1, 10);

            FoodLog entry = createFoodLogWithDirectValues(day10,
                    new BigDecimal("1500"), new BigDecimal("80"),
                    new BigDecimal("150"), new BigDecimal("50"), new BigDecimal("20"),
                    new BigDecimal("300")); // Burned 300 calories

            when(foodLogRepository.findByDateBetweenAndOwnerIdOrderByDateAscIdAsc(
                    any(LocalDate.class), any(LocalDate.class), eq(1L)))
                    .thenReturn(List.of(entry));

            Map<Integer, DayStatus> result = foodLogService.getMonthlyCalendarData(month, userWithTargets);

            DayStatus day10Status = result.get(10);
            assertThat(day10Status.calories()).isEqualByComparingTo(new BigDecimal("1500"));
            assertThat(day10Status.netCalories()).isEqualByComparingTo(new BigDecimal("1200")); // 1500 - 300
        }

        @Test
        @DisplayName("Should return allTargetsMet=null for user without targets")
        void shouldReturnNullTargetsMetForUserWithoutTargets() {
            YearMonth month = YearMonth.of(2024, 1);
            LocalDate day5 = LocalDate.of(2024, 1, 5);

            FoodLog entry = createFoodLogWithDirectValues(day5,
                    new BigDecimal("2500"), new BigDecimal("120"),
                    new BigDecimal("300"), new BigDecimal("80"), new BigDecimal("30"), null);

            when(foodLogRepository.findByDateBetweenAndOwnerIdOrderByDateAscIdAsc(
                    any(LocalDate.class), any(LocalDate.class), eq(2L)))
                    .thenReturn(List.of(entry));

            Map<Integer, DayStatus> result = foodLogService.getMonthlyCalendarData(month, userWithoutTargets);

            DayStatus day5Status = result.get(5);
            assertThat(day5Status.hasEntries()).isTrue();
            assertThat(day5Status.allTargetsMet()).isNull(); // No targets set
        }

        @Test
        @DisplayName("Should return allTargetsMet=true when all targets are met")
        void shouldReturnTrueWhenAllTargetsMet() {
            YearMonth month = YearMonth.of(2024, 1);
            LocalDate day20 = LocalDate.of(2024, 1, 20);

            // All targets met:
            // - netCalories < 2000 ✓
            // - protein >= 100 ✓
            // - fiber >= 25 ✓
            // - carbs < 250 ✓ (note: carbs check is < not >=)
            // - fat >= 65 ✓
            // - cost < 15 ✓
            FoodLog entry = createFoodLogWithDirectValues(day20,
                    new BigDecimal("1800"), // netCal = 1800 < 2000 ✓
                    new BigDecimal("110"),   // protein >= 100 ✓
                    new BigDecimal("200"),   // carbs < 250 ✓
                    new BigDecimal("70"),    // fat >= 65 ✓
                    new BigDecimal("30"),    // fiber >= 25 ✓
                    null);
            // Note: cost defaults to 0 which is < 15 ✓

            when(foodLogRepository.findByDateBetweenAndOwnerIdOrderByDateAscIdAsc(
                    any(LocalDate.class), any(LocalDate.class), eq(1L)))
                    .thenReturn(List.of(entry));

            Map<Integer, DayStatus> result = foodLogService.getMonthlyCalendarData(month, userWithTargets);

            DayStatus day20Status = result.get(20);
            assertThat(day20Status.hasEntries()).isTrue();
            assertThat(day20Status.allTargetsMet()).isTrue();
        }

        @Test
        @DisplayName("Should return allTargetsMet=false when calories exceed target")
        void shouldReturnFalseWhenCaloriesExceedTarget() {
            YearMonth month = YearMonth.of(2024, 1);
            LocalDate day25 = LocalDate.of(2024, 1, 25);

            // Calories exceed target (2500 > 2000)
            FoodLog entry = createFoodLogWithDirectValues(day25,
                    new BigDecimal("2500"), // netCal = 2500 >= 2000 ✗
                    new BigDecimal("110"),
                    new BigDecimal("200"),
                    new BigDecimal("70"),
                    new BigDecimal("30"),
                    null);

            when(foodLogRepository.findByDateBetweenAndOwnerIdOrderByDateAscIdAsc(
                    any(LocalDate.class), any(LocalDate.class), eq(1L)))
                    .thenReturn(List.of(entry));

            Map<Integer, DayStatus> result = foodLogService.getMonthlyCalendarData(month, userWithTargets);

            DayStatus day25Status = result.get(25);
            assertThat(day25Status.hasEntries()).isTrue();
            assertThat(day25Status.allTargetsMet()).isFalse();
        }

        @Test
        @DisplayName("Should return allTargetsMet=false when protein is below target")
        void shouldReturnFalseWhenProteinBelowTarget() {
            YearMonth month = YearMonth.of(2024, 1);
            LocalDate day26 = LocalDate.of(2024, 1, 26);

            // Protein below target (50 < 100)
            FoodLog entry = createFoodLogWithDirectValues(day26,
                    new BigDecimal("1800"),
                    new BigDecimal("50"), // protein < 100 ✗
                    new BigDecimal("200"),
                    new BigDecimal("70"),
                    new BigDecimal("30"),
                    null);

            when(foodLogRepository.findByDateBetweenAndOwnerIdOrderByDateAscIdAsc(
                    any(LocalDate.class), any(LocalDate.class), eq(1L)))
                    .thenReturn(List.of(entry));

            Map<Integer, DayStatus> result = foodLogService.getMonthlyCalendarData(month, userWithTargets);

            assertThat(result.get(26).allTargetsMet()).isFalse();
        }

        @Test
        @DisplayName("Should return allTargetsMet=false when fiber is below target")
        void shouldReturnFalseWhenFiberBelowTarget() {
            YearMonth month = YearMonth.of(2024, 1);
            LocalDate day27 = LocalDate.of(2024, 1, 27);

            // Fiber below target (10 < 25)
            FoodLog entry = createFoodLogWithDirectValues(day27,
                    new BigDecimal("1800"),
                    new BigDecimal("110"),
                    new BigDecimal("200"),
                    new BigDecimal("70"),
                    new BigDecimal("10"), // fiber < 25 ✗
                    null);

            when(foodLogRepository.findByDateBetweenAndOwnerIdOrderByDateAscIdAsc(
                    any(LocalDate.class), any(LocalDate.class), eq(1L)))
                    .thenReturn(List.of(entry));

            Map<Integer, DayStatus> result = foodLogService.getMonthlyCalendarData(month, userWithTargets);

            assertThat(result.get(27).allTargetsMet()).isFalse();
        }

        @Test
        @DisplayName("Should handle multiple entries across different days")
        void shouldHandleMultipleEntriesAcrossDays() {
            YearMonth month = YearMonth.of(2024, 2); // February 2024 (leap year, 29 days)
            LocalDate day1 = LocalDate.of(2024, 2, 1);
            LocalDate day15 = LocalDate.of(2024, 2, 15);
            LocalDate day29 = LocalDate.of(2024, 2, 29);

            FoodLog entry1 = createFoodLogWithDirectValues(day1, new BigDecimal("500"), new BigDecimal("30"),
                    new BigDecimal("60"), new BigDecimal("20"), new BigDecimal("10"), null);
            FoodLog entry15 = createFoodLogWithDirectValues(day15, new BigDecimal("800"), new BigDecimal("50"),
                    new BigDecimal("90"), new BigDecimal("35"), new BigDecimal("15"), null);
            FoodLog entry29 = createFoodLogWithDirectValues(day29, new BigDecimal("1200"), new BigDecimal("70"),
                    new BigDecimal("120"), new BigDecimal("45"), new BigDecimal("20"), null);

            when(foodLogRepository.findByDateBetweenAndOwnerIdOrderByDateAscIdAsc(
                    any(LocalDate.class), any(LocalDate.class), eq(1L)))
                    .thenReturn(List.of(entry1, entry15, entry29));

            Map<Integer, DayStatus> result = foodLogService.getMonthlyCalendarData(month, userWithTargets);

            assertThat(result).hasSize(29); // February 2024 is a leap year

            assertThat(result.get(1).hasEntries()).isTrue();
            assertThat(result.get(1).calories()).isEqualByComparingTo(new BigDecimal("500"));

            assertThat(result.get(2).hasEntries()).isFalse();

            assertThat(result.get(15).hasEntries()).isTrue();
            assertThat(result.get(15).calories()).isEqualByComparingTo(new BigDecimal("800"));

            assertThat(result.get(29).hasEntries()).isTrue();
            assertThat(result.get(29).calories()).isEqualByComparingTo(new BigDecimal("1200"));
        }

        @Test
        @DisplayName("Should use findByDateBetweenOrderByDateAscIdAsc when user is null")
        void shouldUseAlternateRepositoryMethodWhenUserIsNull() {
            YearMonth month = YearMonth.of(2024, 1);
            LocalDate day10 = LocalDate.of(2024, 1, 10);

            FoodLog entry = createFoodLogWithDirectValues(day10,
                    new BigDecimal("1000"), new BigDecimal("60"),
                    new BigDecimal("100"), new BigDecimal("40"), new BigDecimal("15"), null);

            when(foodLogRepository.findByDateBetweenOrderByDateAscIdAsc(
                    any(LocalDate.class), any(LocalDate.class)))
                    .thenReturn(List.of(entry));

            Map<Integer, DayStatus> result = foodLogService.getMonthlyCalendarData(month, null);

            assertThat(result.get(10).hasEntries()).isTrue();
            assertThat(result.get(10).calories()).isEqualByComparingTo(new BigDecimal("1000"));
            // With null user, allTargetsMet should be null (no targets to check)
            assertThat(result.get(10).allTargetsMet()).isNull();
        }

        @Test
        @DisplayName("Should aggregate caloriesSpent from multiple entries")
        void shouldAggregateCaloriesSpentFromMultipleEntries() {
            YearMonth month = YearMonth.of(2024, 1);
            LocalDate day12 = LocalDate.of(2024, 1, 12);

            FoodLog entry1 = createFoodLogWithDirectValues(day12,
                    new BigDecimal("800"), new BigDecimal("40"),
                    new BigDecimal("80"), new BigDecimal("30"), new BigDecimal("10"),
                    new BigDecimal("200")); // Burned 200

            FoodLog entry2 = createFoodLogWithDirectValues(day12,
                    new BigDecimal("600"), new BigDecimal("35"),
                    new BigDecimal("70"), new BigDecimal("25"), new BigDecimal("8"),
                    new BigDecimal("100")); // Burned 100

            when(foodLogRepository.findByDateBetweenAndOwnerIdOrderByDateAscIdAsc(
                    any(LocalDate.class), any(LocalDate.class), eq(1L)))
                    .thenReturn(List.of(entry1, entry2));

            Map<Integer, DayStatus> result = foodLogService.getMonthlyCalendarData(month, userWithTargets);

            DayStatus day12Status = result.get(12);
            assertThat(day12Status.calories()).isEqualByComparingTo(new BigDecimal("1400")); // 800 + 600
            assertThat(day12Status.netCalories()).isEqualByComparingTo(new BigDecimal("1100")); // 1400 - 300
        }
    }
}