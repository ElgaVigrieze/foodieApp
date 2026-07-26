package com.foodie.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.UUID;

@Component
@Slf4j
@RequiredArgsConstructor
public class DataOwnershipMigration {

    private final DataSource dataSource;

    @EventListener(ApplicationReadyEvent.class)
    @Order(1)
    public void onStartup() {
        assignUnclaimedData();
    }

    // Also run every 30 seconds to catch data that becomes orphaned
    @Scheduled(fixedRate = 30000, initialDelay = 10000)
    public void scheduledAssignment() {
        assignUnclaimedData();
    }

    private void assignUnclaimedData() {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {

            // Find first household
            ResultSet rs = stmt.executeQuery("SELECT id FROM households LIMIT 1");
            if (!rs.next()) {
                // No household yet - check if there's a user without one
                rs = stmt.executeQuery("SELECT id FROM app_users WHERE household_id IS NULL LIMIT 1");
                if (rs.next()) {
                    long userId = rs.getLong(1);
                    String code = "FOOD-" + UUID.randomUUID().toString().substring(0, 4).toUpperCase();
                    stmt.executeUpdate("INSERT INTO households (name, invite_code) VALUES ('My Household', '" + code + "')");
                    rs = stmt.executeQuery("SELECT id FROM households ORDER BY id DESC LIMIT 1");
                    if (rs.next()) {
                        long hhId = rs.getLong(1);
                        stmt.executeUpdate("UPDATE app_users SET household_id = " + hhId + ", household_role = 'OWNER' WHERE id = " + userId);
                        log.info("Created household {} for user {}", hhId, userId);
                        assignDataToHousehold(stmt, hhId);
                    }
                }
                return;
            }

            long hhId = rs.getLong(1);

            // Assign users without household
            int users = stmt.executeUpdate("UPDATE app_users SET household_id = " + hhId + ", household_role = 'OWNER' WHERE household_id IS NULL");

            // Assign orphan data
            assignDataToHousehold(stmt, hhId);

            if (users > 0) {
                log.info("Assigned {} users to household {}", users, hhId);
            }
        } catch (Exception e) {
            // Silently ignore - table might not exist yet
        }
    }

    private void assignDataToHousehold(Statement stmt, long hhId) {
        try {
            int p = stmt.executeUpdate("UPDATE products SET household_id = " + hhId + " WHERE household_id IS NULL");
            int m = stmt.executeUpdate("UPDATE meals SET household_id = " + hhId + " WHERE household_id IS NULL");
            int mp = stmt.executeUpdate("UPDATE meal_plans SET household_id = " + hhId + " WHERE household_id IS NULL");
            int fl = stmt.executeUpdate("UPDATE food_logs SET household_id = " + hhId + " WHERE household_id IS NULL");
                        // Remove duplicate meal plans (same week_start + household_id, keep lowest id)
            try {
                stmt.executeUpdate("DELETE FROM meal_plan_entries WHERE meal_plan_id IN (SELECT mp.id FROM meal_plans mp WHERE mp.id NOT IN (SELECT MIN(id) FROM meal_plans GROUP BY week_start, household_id))");
                stmt.executeUpdate("DELETE FROM meal_plans WHERE id NOT IN (SELECT MIN(id) FROM meal_plans GROUP BY week_start, household_id)");
            } catch (Exception e) { /* ignore */ }
            if (p + m + mp + fl > 0) {
                log.info("Assigned to household {}: {} products, {} meals, {} plans, {} logs", hhId, p, m, mp, fl);
            }
        } catch (Exception e) {
            log.debug("Data assignment: {}", e.getMessage());
        }
    }
}