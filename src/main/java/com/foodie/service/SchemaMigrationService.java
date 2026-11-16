package com.foodie.service;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Drops stale columns that Hibernate's ddl-auto=update won't remove.
 * Only active when Flyway is off (local H2 development); with Flyway the same statements live in
 * {@code db/migration/V2__drop_stale_columns.sql}.
 * All other schema changes are handled automatically by Hibernate.
 */
@Component
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
        name = "spring.flyway.enabled", havingValue = "false", matchIfMissing = true)
@RequiredArgsConstructor
@Slf4j
public class SchemaMigrationService {

    private final JdbcTemplate jdbcTemplate;

    @PostConstruct
    public void migrate() {
        // Drop stale columns removed from entities (Hibernate won't do this)
        dropColumnIfExists("workouts", "completed");
        dropColumnIfExists("workouts", "date");
        dropColumnIfExists("app_users", "brain_last_seen_agent");
        dropColumnIfExists("app_users", "brain_last_seen_page");
    }

    private void dropColumnIfExists(String table, String column) {
        try {
            jdbcTemplate.execute("ALTER TABLE " + table + " DROP COLUMN IF EXISTS " + column);
            log.debug("Ensured stale column {}.{} is absent", table, column);
        } catch (DataAccessException e) {
            // DROP COLUMN IF EXISTS does not fail for a missing column, so this is a real problem
            // (permissions, connection, missing table). Not fatal for startup, but never silent.
            log.warn("Could not drop stale column {}.{}: {}", table, column, e.getMessage());
        }
    }
}
