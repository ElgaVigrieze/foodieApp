package com.foodie.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SchemaManagementGuardTest {

    private static DataSource database(String productName) throws SQLException {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        DatabaseMetaData metaData = mock(DatabaseMetaData.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.getMetaData()).thenReturn(metaData);
        when(metaData.getDatabaseProductName()).thenReturn(productName);
        return dataSource;
    }

    private static MockEnvironment env(String flywayEnabled, String ddlAuto) {
        return new MockEnvironment()
                .withProperty("spring.flyway.enabled", flywayEnabled)
                .withProperty("spring.jpa.hibernate.ddl-auto", ddlAuto);
    }

    @Test
    void postgresWithFlywayAndValidateStarts() {
        assertThatCode(() -> new SchemaManagementGuard(database("PostgreSQL"), env("true", "validate")))
                .doesNotThrowAnyException();
    }

    @Test
    void postgresWithFlywayAndNoDdlAutoStarts() {
        assertThatCode(() -> new SchemaManagementGuard(database("PostgreSQL"), env("true", "none")))
                .doesNotThrowAnyException();
    }

    @Test
    void postgresWithoutFlywayRefusesToStart() {
        assertThatThrownBy(() -> new SchemaManagementGuard(database("PostgreSQL"), env("false", "validate")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("FLYWAY_ENABLED=true");
    }

    @Test
    void postgresWithTheOldDefaultsRefusesToStart() {
        assertThatThrownBy(() -> new SchemaManagementGuard(database("PostgreSQL"), env("false", "update")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ddl-auto=update");
    }

    @Test
    void postgresWhereHibernateChangesTheSchemaRefusesToStart() {
        for (String ddlAuto : new String[] {"update", "create", "create-drop", "UPDATE "}) {
            assertThatThrownBy(() -> new SchemaManagementGuard(database("PostgreSQL"), env("true", ddlAuto)))
                    .as(ddlAuto)
                    .isInstanceOf(IllegalStateException.class);
        }
    }

    @Test
    void h2KeepsTheLocalDevelopmentDefaults() {
        assertThatCode(() -> new SchemaManagementGuard(database("H2"), env("false", "update")))
                .doesNotThrowAnyException();
    }
}
