package com.foodie.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import javax.sql.DataSource;
import java.sql.*;
import java.util.StringJoiner;

/**
 * Exports all data as PostgreSQL-compatible INSERT statements.
 * Access: GET /api/admin/export-sql
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminApiController {

    private final DataSource dataSource;

    @GetMapping(value = "/export-sql", produces = MediaType.TEXT_PLAIN_VALUE)
    public String exportSql() throws Exception {
        StringBuilder sql = new StringBuilder();
        sql.append("-- Foodie full data export\n");
        sql.append("-- Generated: ").append(java.time.LocalDateTime.now()).append("\n");
        sql.append("-- Import with: psql $DATABASE_URL < foodie-export.sql\n\n");

        try (Connection conn = dataSource.getConnection()) {
            exportTable(conn, sql, "app_users",
                "id, google_id, email, name, picture_url, " +
                "target_calories, target_fiber, target_carbs, target_fat, target_protein, target_cost");
            exportTable(conn, sql, "households", "id, name, invite_code");
            exportTable(conn, sql, "products",
                "id, name, category, unit, price_per_unit, calories, protein, carbs, fat, fiber, sugar");
            exportTable(conn, sql, "meals",
                "id, name, category, servings, recipe, recipe_url, favorite, archived, user_id, household_id");
            exportTable(conn, sql, "meal_ingredients", "id, meal_id, product_id, quantity");
            exportTable(conn, sql, "meal_plans", "id, name, week_start, frozen, user_id, household_id");
            exportTable(conn, sql, "meal_plan_entries",
                "id, meal_plan_id, day_of_week, slot, servings, prepared, meal_id");
            exportTable(conn, sql, "shopping_list_items",
                "id, week_start, product_id, adjusted_quantity, checked, user_id, household_id");
            exportTable(conn, sql, "food_logs",
                "id, date, slot, meal_id, servings_consumed, product_id, product_quantity, " +
                "calories_spent, photo_description, direct_calories, direct_protein, " +
                "direct_carbs, direct_fat, direct_fiber, user_id, household_id");
            exportTable(conn, sql, "workouts",
                "id, name, type, notes, youtube_url, total_minutes, estimated_kcal, sets_json, owner_id");
            exportTable(conn, sql, "training_plan_entries",
                "id, date, completed, workout_id, owner_id");
        }

        sql.append("\n-- Reset sequences\n");
        for (String t : new String[]{"app_users","households","products","meals",
                "meal_ingredients","meal_plans","meal_plan_entries",
                "shopping_list_items","food_logs","workouts","training_plan_entries"}) {
            sql.append("SELECT setval('").append(t).append("_id_seq', ")
               .append("(SELECT COALESCE(MAX(id), 1) FROM ").append(t).append("), true);\n");
        }

        return sql.toString();
    }

    private void exportTable(Connection conn, StringBuilder sql, String table, String columns) {
        sql.append("-- ").append(table.toUpperCase()).append("\n");
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT " + columns + " FROM " + table + " ORDER BY id")) {

            ResultSetMetaData meta = rs.getMetaData();
            int colCount = meta.getColumnCount();
            int rows = 0;

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
                    } else if (val instanceof java.sql.Date d) {
                        vals.add("'" + d.toString() + "'");
                    } else {
                        vals.add("'" + val.toString().replace("'", "''") + "'");
                    }
                }
                sql.append(vals).append(") ON CONFLICT (id) DO NOTHING;\n");
                rows++;
            }
            sql.append("-- ").append(rows).append(" rows\n\n");

        } catch (SQLException e) {
            sql.append("-- SKIPPED ").append(table).append(": ").append(e.getMessage()).append("\n\n");
        }
    }
}