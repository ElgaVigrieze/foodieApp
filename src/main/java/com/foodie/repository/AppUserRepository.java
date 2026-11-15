package com.foodie.repository;

import com.foodie.model.db.AppUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {
    Optional<AppUser> findByGoogleId(String googleId);

    Optional<AppUser> findByBrainToken(String brainToken);

    /** Sets the token only if the user has none yet; returns the number of rows changed. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update AppUser u set u.brainToken = :token, u.brainTokenCreatedAt = :now "
            + "where u.id = :id and (u.brainToken is null or u.brainToken = '')")
    int claimBrainToken(@Param("id") Long id, @Param("token") String token, @Param("now") Instant now);
}
