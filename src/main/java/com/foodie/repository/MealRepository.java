package com.foodie.repository;

import com.foodie.model.Meal;
import com.foodie.model.MealCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface MealRepository extends JpaRepository<Meal, Long> {
    // Active (non-archived) queries
    List<Meal> findByArchivedFalseOrderByNameAsc();
    List<Meal> findByArchivedFalseAndFavoriteTrueOrderByNameAsc();
    List<Meal> findByHouseholdIdAndArchivedFalseOrderByNameAsc(Long householdId);
    List<Meal> findByHouseholdIdAndArchivedFalseAndFavoriteTrueOrderByNameAsc(Long householdId);
    // Archived
    List<Meal> findByArchivedTrueOrderByNameAsc();
    List<Meal> findByHouseholdIdAndArchivedTrueOrderByNameAsc(Long householdId);
    // Legacy / still needed
    List<Meal> findAllByOrderByNameAsc();
    List<Meal> findByCategoryOrderByNameAsc(MealCategory category);
    List<Meal> findByFavoriteTrueOrderByNameAsc();
    List<Meal> findByHouseholdIdOrderByNameAsc(Long householdId);
    List<Meal> findByHouseholdIdAndFavoriteTrueOrderByNameAsc(Long householdId);
}
