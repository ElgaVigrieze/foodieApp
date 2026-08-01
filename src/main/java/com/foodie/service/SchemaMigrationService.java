package com.foodie.service;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Adds missing columns and updates constraints on existing databases.
 * Handles both H2 (local) and PostgreSQL (production).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SchemaMigrationService {

    private final JdbcTemplate jdbcTemplate;

    @Value("${spring.datasource.url:}")
    private String datasourceUrl;

    @PostConstruct
    public void migrate() {
        boolean isPostgres = datasourceUrl.contains("postgresql");

        // Add new columns
        addColumnIfNotExists("meal_plans", "frozen", "BOOLEAN DEFAULT FALSE");
        addColumnIfNotExists("meal_plan_entries", "prepared", "BOOLEAN DEFAULT FALSE");
        addColumnIfNotExists("training_plan_entries", "completed", "BOOLEAN DEFAULT FALSE");

        // Drop stale columns that were removed from entities
        dropColumnIfExists("workouts", "completed");
        dropColumnIfExists("workouts", "date");
        addColumnIfNotExists("workouts", "estimated_kcal", "INTEGER");
        addColumnIfNotExists("workouts", "actual_kcal", "INTEGER");

        // Update check constraints for new enum values (PostgreSQL only - H2 doesn't add these)
        if (isPostgres) {
            dropAndRecreateCheckConstraint("meals", "meals_category_check",
                    "category IN ('MAIN_COURSE','SOUP','SALAD','SNACK','DESSERT','DRINK','READY_MEAL')");
            // No new enum constraints needed for fitness tables - they use VARCHAR
        }
    }

    private void addColumnIfNotExists(String table, String column, String definition) {
        try {
            jdbcTemplate.execute(
                    "ALTER TABLE " + table + " ADD COLUMN IF NOT EXISTS " + column + " " + definition);
            log.info("Ensured column {}.{} exists", table, column);
        } catch (Exception e) {
            log.warn("Could not add column {}.{}: {}", table, column, e.getMessage());
        }
    }

    private void dropColumnIfExists(String table, String column) {
        try {
            jdbcTemplate.execute("ALTER TABLE " + table + " DROP COLUMN IF EXISTS " + column);
            log.info("Dropped stale column {}.{}", table, column);
        } catch (Exception e) {
            log.warn("Could not drop column {}.{}: {}", table, column, e.getMessage());
        }
    }

    private void dropAndRecreateCheckConstraint(String table, String constraintName, String checkExpression) {
        try {
            jdbcTemplate.execute("ALTER TABLE " + table + " DROP CONSTRAINT IF EXISTS " + constraintName);
            jdbcTemplate.execute("ALTER TABLE " + table + " ADD CONSTRAINT " + constraintName
                    + " CHECK (" + checkExpression + ")");
            log.info("Updated constraint {} on table {}", constraintName, table);
        } catch (Exception e) {
            log.warn("Could not update constraint {} on {}: {}", constraintName, table, e.getMessage());
        }
    }
}