package com.foodie.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * One PostgreSQL container shared by all Spring integration tests (import it with
 * {@code @Import(PostgresTestConfig.class)}). {@link ServiceConnection} points the application's
 * DataSource at it automatically, and Flyway then builds the schema from {@code db/migration}, so
 * the tests also exercise the real migrations. Requires Docker.
 *
 * <p>The container is started once per JVM and cleaned up by Testcontainers when the JVM exits,
 * which is why the bean disables its own destroy method: Spring contexts are cached and reused
 * across test classes, and none of them may stop the container for the others.
 */
@TestConfiguration(proxyBeanMethods = false)
public class PostgresTestConfig {

    // Same major version as production.
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:18"));

    static {
        POSTGRES.start();
    }

    @Bean(destroyMethod = "")
    @ServiceConnection
    PostgreSQLContainer<?> postgres() {
        return POSTGRES;
    }
}
