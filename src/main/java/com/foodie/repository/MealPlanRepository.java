package com.foodie.repository;

import com.foodie.model.db.MealPlan;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface MealPlanRepository extends JpaRepository<MealPlan, Long> {
    Optional<MealPlan> findByWeekStart(LocalDate weekStart);
    Optional<MealPlan> findByWeekStartAndHouseholdId(LocalDate weekStart, Long householdId);
    List<MealPlan> findByHouseholdId(Long householdId);
}