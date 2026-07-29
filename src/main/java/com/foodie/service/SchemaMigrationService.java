package com.foodie.service;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Adds missing columns to existing H2 database tables.
 * Hibernate's ddl-auto=update sometimes fails to add columns to an existing H2 file DB.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SchemaMigrationService {

    private final JdbcTemplate jdbcTemplate;

    @PostConstruct
    public void migrate() {
        addColumnIfNotExists("meal_plans", "frozen", "BOOLEAN DEFAULT FALSE");
        addColumnIfNotExists("meal_plan_entries", "prepared", "BOOLEAN DEFAULT FALSE");
    }

    private void addColumnIfNotExists(String table, String column, String definition) {
        try {
            jdbcTemplate.execute("ALTER TABLE " + table + " ADD COLUMN IF NOT EXISTS " + column + " " + definition);
            log.info("Ensured column {}.{} exists", table, column);
        } catch (Exception e) {
            log.warn("Could not add column {}.{}: {}", table, column, e.getMessage());
        }
    }
}
