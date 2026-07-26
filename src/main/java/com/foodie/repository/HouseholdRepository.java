package com.foodie.repository;

import com.foodie.model.Household;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface HouseholdRepository extends JpaRepository<Household, Long> {
    Optional<Household> findByInviteCode(String inviteCode);
}
