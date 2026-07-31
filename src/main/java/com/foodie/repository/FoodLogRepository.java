package com.foodie.repository;

import com.foodie.model.FoodLog;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;
import java.util.List;

public interface FoodLogRepository extends JpaRepository<FoodLog, Long> {
    List<FoodLog> findByDateOrderByIdAsc(LocalDate date);
    List<FoodLog> findByDateBetweenOrderByDateAscIdAsc(LocalDate start, LocalDate end);
    List<FoodLog> findByDateAndOwnerIdOrderByIdAsc(LocalDate date, Long ownerId);
    List<FoodLog> findByDateAndHouseholdIdOrderByIdAsc(LocalDate date, Long householdId);
    List<FoodLog> findByDateBetweenAndOwnerIdOrderByDateAscIdAsc(LocalDate start, LocalDate end, Long ownerId);
}
