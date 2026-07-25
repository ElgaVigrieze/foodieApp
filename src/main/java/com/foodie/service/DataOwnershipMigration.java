package com.foodie.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

/**
 * One-time migration: assigns all unclaimed (user_id IS NULL) meals, meal_plans,
 * and food_logs to the first registered user.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class DataOwnershipMigration {

    private final DataSource dataSource;

    @EventListener(ApplicationReadyEvent.class)
    @Order(1)
    public void assignUnclaimedData() {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {

            // Find the first user
            ResultSet rs = stmt.executeQuery("SELECT id FROM app_users ORDER BY id LIMIT 1");
            if (!rs.next()) {
                log.info("No users yet — skipping ownership migration.");
                return;
            }
            long userId = rs.getLong(1);

            int meals = stmt.executeUpdate(
                    "UPDATE meals SET user_id = " + userId + " WHERE user_id IS NULL");
            int plans = stmt.executeUpdate(
                    "UPDATE meal_plans SET user_id = " + userId + " WHERE user_id IS NULL");
            int logs = stmt.executeUpdate(
                    "UPDATE food_logs SET user_id = " + userId + " WHERE user_id IS NULL");

            if (meals + plans + logs > 0) {
                log.info("Ownership migration: assigned {} meals, {} plans, {} logs to user {}",
                        meals, plans, logs, userId);
            }
        } catch (Exception e) {
            log.warn("Ownership migration skipped: {}", e.getMessage());
        }
    }
}
