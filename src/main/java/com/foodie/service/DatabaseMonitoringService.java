package com.foodie.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Monitors database size and usage to prevent runaway costs.
 * Logs warnings when thresholds are exceeded.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DatabaseMonitoringService {

    private final EntityManager entityManager;

    @Value("${app.monitoring.max-users:100}")
    private int maxUsers;

    @Value("${app.monitoring.max-meals-per-user:500}")
    private int maxMealsPerUser;

    @Value("${app.monitoring.max-products-per-user:1000}")
    private int maxProductsPerUser;

    @Value("${app.monitoring.max-food-logs:50000}")
    private int maxFoodLogs;

    @Value("${app.monitoring.admin-email:}")
    private String adminEmail;

    /**
     * Get current database statistics.
     */
    public Map<String, Object> getStats() {
        Map<String, Object> stats = new LinkedHashMap<>();

        stats.put("timestamp", LocalDateTime.now().toString());
        stats.put("totalUsers", countTable("app_user"));
        stats.put("totalHouseholds", countTable("household"));
        stats.put("totalMeals", countTable("meal"));
        stats.put("totalProducts", countTable("product"));
        stats.put("totalFoodLogs", countTable("food_log"));
        stats.put("totalMealPlans", countTable("meal_plan"));
        stats.put("totalShoppingItems", countTable("shopping_list_item"));
        stats.put("totalWorkouts", countTable("workout"));

        // Per-user averages
        long users = (long) stats.get("totalUsers");
        if (users > 0) {
            stats.put("avgMealsPerUser", round((long) stats.get("totalMeals") / (double) users));
            stats.put("avgProductsPerUser", round((long) stats.get("totalProducts") / (double) users));
            stats.put("avgFoodLogsPerUser", round((long) stats.get("totalFoodLogs") / (double) users));
        }

        // Thresholds
        stats.put("thresholds", Map.of(
                "maxUsers", maxUsers,
                "maxMealsPerUser", maxMealsPerUser,
                "maxProductsPerUser", maxProductsPerUser,
                "maxFoodLogs", maxFoodLogs
        ));

        // Warnings
        stats.put("warnings", checkThresholds());

        return stats;
    }

    /**
     * Get top users by data volume.
     */
    public Map<String, Object> getTopUsers() {
        Map<String, Object> result = new LinkedHashMap<>();

        result.put("topByMeals", getTopUsersByTable("meal", 10));
        result.put("topByProducts", getTopUsersByTable("product", 10));
        result.put("topByFoodLogs", getTopUsersByTable("food_log", 10));

        return result;
    }

    /**
     * Run every hour to check thresholds and log warnings.
     */
    @Scheduled(fixedRate = 3600000) // 1 hour
    public void scheduledCheck() {
        var warnings = checkThresholds();
        if (!warnings.isEmpty()) {
            log.warn("⚠️ DATABASE MONITORING ALERTS: {}", warnings);
        } else {
            log.info("Database monitoring: all metrics within thresholds");
        }
    }

    private Map<String, String> checkThresholds() {
        Map<String, String> warnings = new LinkedHashMap<>();

        long users = countTable("app_user");
        if (users > maxUsers) {
            warnings.put("users", "Total users (" + users + ") exceeds limit (" + maxUsers + ")");
        }

        long foodLogs = countTable("food_log");
        if (foodLogs > maxFoodLogs) {
            warnings.put("foodLogs", "Total food logs (" + foodLogs + ") exceeds limit (" + maxFoodLogs + ")");
        }

        // Check for users with excessive data
        var topMealUsers = getTopUsersByTable("meal", 1);
        if (!topMealUsers.isEmpty()) {
            var top = topMealUsers.values().iterator().next();
            if (top instanceof Number n && n.longValue() > maxMealsPerUser) {
                warnings.put("mealsPerUser", "User has " + n + " meals (limit: " + maxMealsPerUser + ")");
            }
        }

        var topProductUsers = getTopUsersByTable("product", 1);
        if (!topProductUsers.isEmpty()) {
            var top = topProductUsers.values().iterator().next();
            if (top instanceof Number n && n.longValue() > maxProductsPerUser) {
                warnings.put("productsPerUser", "User has " + n + " products (limit: " + maxProductsPerUser + ")");
            }
        }

        return warnings;
    }

    private long countTable(String tableName) {
        try {
            Query query = entityManager.createNativeQuery("SELECT COUNT(*) FROM " + tableName);
            Object result = query.getSingleResult();
            return toLong(result);
        } catch (Exception e) {
            log.debug("Could not count table {}: {}", tableName, e.getMessage());
            return 0;
        }
    }

    private Map<String, Long> getTopUsersByTable(String tableName, int limit) {
        Map<String, Long> result = new LinkedHashMap<>();
        try {
            // Most tables have household_id, use that for grouping
            String sql = """
                SELECT h.name, COUNT(*) as cnt
                FROM %s t
                JOIN household h ON t.household_id = h.id
                GROUP BY h.id, h.name
                ORDER BY cnt DESC
                LIMIT %d
                """.formatted(tableName, limit);

            Query query = entityManager.createNativeQuery(sql);
            var rows = query.getResultList();
            for (Object row : rows) {
                Object[] cols = (Object[]) row;
                String name = cols[0] != null ? cols[0].toString() : "Unknown";
                long count = toLong(cols[1]);
                result.put(name, count);
            }
        } catch (Exception e) {
            log.debug("Could not get top users for {}: {}", tableName, e.getMessage());
        }
        return result;
    }

    private long toLong(Object value) {
        if (value instanceof Long l) return l;
        if (value instanceof BigInteger bi) return bi.longValue();
        if (value instanceof BigDecimal bd) return bd.longValue();
        if (value instanceof Number n) return n.longValue();
        return 0;
    }

    private double round(double value) {
        return Math.round(value * 10) / 10.0;
    }
}
