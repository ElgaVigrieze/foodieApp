package com.foodie.repository;

import com.foodie.model.db.TrainingPlanEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.transaction.annotation.Transactional;

public interface TrainingPlanEntryRepository extends JpaRepository<TrainingPlanEntry, Long> {

    List<TrainingPlanEntry> findByOwnerIdAndDateBetweenOrderByDate(Long ownerId, LocalDate start, LocalDate end);

    @Modifying
    @Transactional
    void deleteByWorkoutId(Long workoutId);
}