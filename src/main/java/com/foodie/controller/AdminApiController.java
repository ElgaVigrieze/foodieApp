package com.foodie.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import javax.sql.DataSource;
import java.sql.*;
import java.util.StringJoiner;

/**
 * Exports all data as PostgreSQL-compatible INSERT statements.
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminApiController {

    private final DataSource dataSource;

    @PostMapping("/backup")
    public String backup() throws Exception {
        java.nio.file.Path backupDir = java.nio.file.Path.of("./data/backups");
        java.nio.file.Files.createDirectories(backupDir);
        java.nio.file.Path backupFile = backupDir.resolve("foodie-db_manual-backup.zip").toAbsolutePath();

        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("BACKUP TO '" + backupFile.toString().replace("\\", "/") + "'");
        }

        return "Backup saved to: " + backupFile;
    }

    @GetMapping(value = "/export-sql", produces = MediaType.TEXT_PLAIN_VALUE)
    public String exportSql() throws Exception {
        StringBuilder sql = new StringBuilder();
        sql.append("-- Foodie data export\n");
        sql.append("-- Generated from H2 database\n\n");

        try (Connection conn = dataSource.getConnection()) {
            exportTable(conn, sql, "app_users", "id, google_id, email, name, picture_url");
            exportTable(conn, sql, "products", "id, name, category, unit, price_per_unit, calories, protein, carbs, fat, fiber, sugar");
            exportTable(conn, sql, "meals", "id, name, category, servings, recipe, favorite, user_id");
            exportTable(conn, sql, "meal_ingredients", "id, meal_id, product_id, quantity");
            exportTable(conn, sql, "meal_plans", "id, name, week_start, user_id");
            exportTable(conn, sql, "meal_plan_entries", "id, meal_plan_id, day_of_week, slot, servings, meal_id");
            exportTable(conn, sql, "food_logs", "id, date, meal_id, servings_consumed, user_id");
        }

        // Reset sequences
        sql.append("\n-- Reset sequences\n");
        sql.append("SELECT setval('app_users_id_seq', (SELECT COALESCE(MAX(id), 1) FROM app_users));\n");
        sql.append("SELECT setval('products_id_seq', (SELECT COALESCE(MAX(id), 1) FROM products));\n");
        sql.append("SELECT setval('meals_id_seq', (SELECT COALESCE(MAX(id), 1) FROM meals));\n");
        sql.append("SELECT setval('meal_ingredients_id_seq', (SELECT COALESCE(MAX(id), 1) FROM meal_ingredients));\n");
        sql.append("SELECT setval('meal_plans_id_seq', (SELECT COALESCE(MAX(id), 1) FROM meal_plans));\n");
        sql.append("SELECT setval('meal_plan_entries_id_seq', (SELECT COALESCE(MAX(id), 1) FROM meal_plan_entries));\n");
        sql.append("SELECT setval('food_logs_id_seq', (SELECT COALESCE(MAX(id), 1) FROM food_logs));\n");

        return sql.toString();
    }

    private void exportTable(Connection conn, StringBuilder sql, String table, String columns) throws SQLException {
        sql.append("-- ").append(table).append("\n");
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT " + columns + " FROM " + table)) {

            ResultSetMetaData meta = rs.getMetaData();
            int colCount = meta.getColumnCount();

            while (rs.next()) {
                sql.append("INSERT INTO ").append(table).append(" (").append(columns).append(") VALUES (");
                StringJoiner vals = new StringJoiner(", ");
                for (int i = 1; i <= colCount; i++) {
                    Object val = rs.getObject(i);
                    if (val == null) {
                        vals.add("NULL");
                    } else if (val instanceof Number) {
                        vals.add(val.toString());
                    } else if (val instanceof Boolean b) {
                        vals.add(b ? "true" : "false");
                    } else {
                        vals.add("'" + val.toString().replace("'", "''") + "'");
                    }
                }
                sql.append(vals).append(") ON CONFLICT (id) DO NOTHING;\n");
            }
            sql.append("\n");
        } catch (SQLException e) {
            sql.append("-- Skipped ").append(table).append(": ").append(e.getMessage()).append("\n\n");
        }
    }
}
