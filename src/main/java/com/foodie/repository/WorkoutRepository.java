package com.foodie.repository;

import com.foodie.model.db.Workout;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WorkoutRepository extends JpaRepository<Workout, Long> {

    List<Workout> findByOwnerIdOrderByNameAsc(Long ownerId);
}