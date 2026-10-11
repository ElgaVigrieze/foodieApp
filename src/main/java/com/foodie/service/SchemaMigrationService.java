package com.foodie.service;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Drops stale columns that Hibernate's ddl-auto=update won't remove.
 * All other schema changes are handled automatically by Hibernate.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SchemaMigrationService {

    private final JdbcTemplate jdbcTemplate;

    @PostConstruct
    public void migrate() {
        // Drop stale columns removed from entities (Hibernate won't do this)
        dropColumnIfExists("workouts", "completed");
        dropColumnIfExists("workouts", "date");
    }

    private void dropColumnIfExists(String table, String column) {
        try {
            jdbcTemplate.execute("ALTER TABLE " + table + " DROP COLUMN IF EXISTS " + column);
            log.info("Dropped stale column {}.{}", table, column);
        } catch (Exception e) {
            log.debug("Column {}.{} already removed or doesn't exist", table, column);
        }
    }
}
