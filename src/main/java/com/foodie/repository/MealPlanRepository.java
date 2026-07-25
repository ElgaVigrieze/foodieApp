package com.foodie.repository;

import com.foodie.model.MealPlan;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;

public interface MealPlanRepository extends JpaRepository<MealPlan, Long> {
    Optional<MealPlan> findByWeekStart(LocalDate weekStart);
    Optional<MealPlan> findByWeekStartAndOwnerId(LocalDate weekStart, Long ownerId);
}
