package com.foodie.repository;

import com.foodie.model.Meal;
import com.foodie.model.MealCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface MealRepository extends JpaRepository<Meal, Long> {
    List<Meal> findAllByOrderByNameAsc();
    List<Meal> findByCategoryOrderByNameAsc(MealCategory category);
    List<Meal> findByFavoriteTrueOrderByNameAsc();
    List<Meal> findByOwnerIdOrOwnerIdIsNullOrderByNameAsc(Long ownerId);
    List<Meal> findByOwnerIdAndFavoriteTrueOrderByNameAsc(Long ownerId);
    List<Meal> findByHouseholdIdOrderByNameAsc(Long householdId);
    List<Meal> findByHouseholdIdAndFavoriteTrueOrderByNameAsc(Long householdId);
}
