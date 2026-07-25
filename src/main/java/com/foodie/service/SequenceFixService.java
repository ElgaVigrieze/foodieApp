package com.foodie.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

/**
 * Fixes H2 auto-increment sequences on startup to prevent PK conflicts.
 * Uses raw JDBC to ensure it runs reliably.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class SequenceFixService {

    private final DataSource dataSource;

    @EventListener(ApplicationReadyEvent.class)
    public void fixSequences() {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {

            fixSequence(stmt, "products");
            fixSequence(stmt, "meals");
            fixSequence(stmt, "meal_ingredients");
            fixSequence(stmt, "meal_plan_entries");
            fixSequence(stmt, "meal_plans");
            fixSequence(stmt, "food_logs");
            fixSequence(stmt, "shopping_list_items");

            log.info("Auto-increment sequences fixed successfully.");
        } catch (Exception e) {
            log.warn("Could not fix sequences: {}", e.getMessage());
        }
    }

    private void fixSequence(Statement stmt, String tableName) {
        try {
            ResultSet rs = stmt.executeQuery("SELECT COALESCE(MAX(id), 0) FROM " + tableName);
            if (rs.next()) {
                long maxId = rs.getLong(1);
                long nextVal = Math.max(maxId + 1, 1000);
                stmt.executeUpdate("ALTER TABLE " + tableName + " ALTER COLUMN id RESTART WITH " + nextVal);
                log.debug("Sequence for {} set to {}", tableName, nextVal);
            }
        } catch (Exception e) {
            // Table might not exist yet on first run
            log.debug("Skipping sequence fix for {}: {}", tableName, e.getMessage());
        }
    }
}
