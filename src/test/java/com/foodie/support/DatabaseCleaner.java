package com.foodie.support;

import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Empties every application table between tests, regardless of foreign keys. Integration test
 * classes share one database (and one Spring context), so each starts from a clean slate instead
 * of deleting its own rows in dependency order. PostgreSQL only.
 */
public final class DatabaseCleaner {

    private static final String MIGRATION_TABLE = "flyway_schema_history";

    private DatabaseCleaner() {}

    public static void clean(JdbcTemplate jdbc) {
        List<String> tables = jdbc.queryForList(
                "select tablename from pg_tables where schemaname = current_schema() and tablename <> ?",
                String.class, MIGRATION_TABLE);
        if (tables.isEmpty()) return;
        String list = tables.stream().map(t -> "\"" + t + "\"").collect(Collectors.joining(", "));
        jdbc.execute("TRUNCATE TABLE " + list + " RESTART IDENTITY CASCADE");
    }
}
