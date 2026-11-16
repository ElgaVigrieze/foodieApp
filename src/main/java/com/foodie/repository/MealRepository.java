package com.foodie.repository;

import com.foodie.model.db.Meal;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

/** Meals belong to a household and are listed per household. */
public interface MealRepository extends JpaRepository<Meal, Long> {
    List<Meal> findByHouseholdIdAndArchivedFalseOrderByNameAsc(Long householdId);

    List<Meal> findByHouseholdIdAndArchivedFalseAndFavoriteTrueOrderByNameAsc(Long householdId);

    List<Meal> findByHouseholdIdAndArchivedTrueOrderByNameAsc(Long householdId);
}
