package com.foodie.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.orm.jpa.EntityManagerFactoryDependsOnPostProcessor;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Locale;
import java.util.Set;

/**
 * Refuses to start against PostgreSQL unless Flyway owns the schema.
 *
 * <p>On PostgreSQL the schema, and the data changes that come with it, are defined by the migrations
 * in {@code db/migration}. If Flyway is off, or Hibernate is allowed to change the schema itself,
 * Hibernate silently adds new columns but none of the data migrations run: for example the shopping
 * list ownership columns would appear empty, and every existing list would vanish for its household.
 * Failing at startup turns that into a clear configuration error.
 *
 * <p>The check runs before the {@code entityManagerFactory}, i.e. before Hibernate touches the schema.
 * Local development on H2 is not affected.
 */
@Component(SchemaManagementGuard.BEAN_NAME)
@Slf4j
public class SchemaManagementGuard {

    static final String BEAN_NAME = "schemaManagementGuard";

    /** ddl-auto values with which Hibernate changes the schema itself. */
    private static final Set<String> SCHEMA_CHANGING_DDL_AUTO = Set.of("update", "create", "create-drop", "create-only");

    public SchemaManagementGuard(DataSource dataSource, Environment env) throws SQLException {
        String database;
        try (Connection connection = dataSource.getConnection()) {
            database = connection.getMetaData().getDatabaseProductName();
        }
        if (!"PostgreSQL".equalsIgnoreCase(database)) {
            return;
        }

        boolean flywayEnabled = env.getProperty("spring.flyway.enabled", Boolean.class, true);
        String ddlAuto = env.getProperty("spring.jpa.hibernate.ddl-auto", "none").trim().toLowerCase(Locale.ROOT);

        if (!flywayEnabled || SCHEMA_CHANGING_DDL_AUTO.contains(ddlAuto)) {
            throw new IllegalStateException("""
                    Refusing to start: on PostgreSQL the schema must be managed by Flyway, but \
                    spring.flyway.enabled=%s and spring.jpa.hibernate.ddl-auto=%s. \
                    Set FLYWAY_ENABLED=true and DDL_AUTO=validate (for Fly.io: \
                    fly secrets set FLYWAY_ENABLED=true DDL_AUTO=validate). \
                    Otherwise Hibernate adds new columns without running the data migrations."""
                    .formatted(flywayEnabled, ddlAuto));
        }
        log.info("Schema check passed: PostgreSQL with Flyway, ddl-auto={}", ddlAuto);
    }

    /** Makes Hibernate's EntityManagerFactory wait for the check above. */
    @Configuration(proxyBeanMethods = false)
    static class RunBeforeHibernate extends EntityManagerFactoryDependsOnPostProcessor {
        RunBeforeHibernate() {
            super(BEAN_NAME);
        }
    }
}
