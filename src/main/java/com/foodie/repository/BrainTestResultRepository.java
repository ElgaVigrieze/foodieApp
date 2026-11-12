package com.foodie.repository;

import com.foodie.model.BrainTestType;
import com.foodie.model.db.BrainTestResult;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface BrainTestResultRepository extends JpaRepository<BrainTestResult, Long> {

    List<BrainTestResult> findByOwnerIdAndTestDateBetweenOrderByTestDateDescRecordedAtDesc(
            Long ownerId, LocalDate from, LocalDate to);

    Optional<BrainTestResult> findTopByOwnerIdAndTestTypeOrderByRecordedAtDesc(
            Long ownerId, BrainTestType testType);

    Optional<BrainTestResult> findByOwnerIdAndClientKey(Long ownerId, String clientKey);
}
