package com.poultryprophet.integration;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Applies the proposed migration only in a synthetic schema of the disposable local cluster. */
@EnabledIfEnvironmentVariable(named = "POULTRY_DISPOSABLE_DB_URL",
        matches = "jdbc:postgresql://127\\.0\\.0\\.1:(25432|25436)/poultry_prophet_validation")
class DisposableExpenseCorrectionMigrationIntegrationTest {
    private final String url = System.getenv("POULTRY_DISPOSABLE_DB_URL");

    @Test
    void upgradePreservesExistingExpensesAndEnforcesCorrectionLinks() throws Exception {
        String schema = newSchema();
        try {
            execute("CREATE TABLE " + schema + ".farm_financial_transaction (id bigint PRIMARY KEY, description text NOT NULL)");
            execute("INSERT INTO " + schema + ".farm_financial_transaction VALUES (1, 'existing expense'), (2, 'unrelated expense')");
            Flyway flyway = flyway(schema);
            flyway.baseline();

            assertThat(flyway.migrate().migrationsExecuted).isEqualTo(1);
            assertThat(flyway.migrate().migrationsExecuted).isZero();
            flyway.validate();
            try (var connection = connection(); var statement = connection.createStatement();
                 var columns = statement.executeQuery("SELECT data_type FROM information_schema.columns WHERE table_schema = '" + schema + "' AND table_name = 'farm_financial_transaction' AND column_name = 'correction_expected_updated_at'")) {
                assertThat(columns.next()).isTrue();
                assertThat(columns.getString(1)).isEqualTo("timestamp with time zone");
            }
            try (var connection = connection(); var statement = connection.createStatement();
                 var rows = statement.executeQuery("SELECT id, description, correction_of_id, correction_expected_updated_at FROM " + schema + ".farm_financial_transaction ORDER BY id")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getLong(1)).isEqualTo(1);
                assertThat(rows.getString(2)).isEqualTo("existing expense");
                assertThat(rows.getObject(3)).isNull();
                assertThat(rows.getObject(4)).isNull();
                assertThat(rows.next()).isTrue();
                assertThat(rows.getString(2)).isEqualTo("unrelated expense");
                assertThat(rows.next()).isFalse();
            }
            execute("INSERT INTO " + schema + ".farm_financial_transaction (id, description, correction_of_id, correction_expected_updated_at) VALUES (3, 'correction', 1, '2026-11-27T00:00:00Z')");
            assertThatThrownBy(() -> execute("INSERT INTO " + schema + ".farm_financial_transaction (id, description, correction_of_id) VALUES (4, 'duplicate', 1)"))
                    .isInstanceOf(SQLException.class).hasMessageContaining("uq_expense_correction_original");
            assertThatThrownBy(() -> execute("INSERT INTO " + schema + ".farm_financial_transaction (id, description, correction_of_id) VALUES (5, 'orphan', 999)"))
                    .isInstanceOf(SQLException.class).hasMessageContaining("foreign key");
        } finally {
            execute("DROP SCHEMA " + schema + " CASCADE");
        }
    }

    @Test
    void incompatibleExistingColumnFailsWithoutPartiallyApplyingMigration() throws Exception {
        String schema = newSchema();
        try {
            execute("CREATE TABLE " + schema + ".farm_financial_transaction (id bigint PRIMARY KEY, description text NOT NULL, correction_of_id text)");
            execute("INSERT INTO " + schema + ".farm_financial_transaction VALUES (1, 'preserve me', NULL)");
            Flyway flyway = flyway(schema);
            flyway.baseline();

            assertThatThrownBy(flyway::migrate).isInstanceOf(FlywayException.class);
            try (var connection = connection(); var statement = connection.createStatement();
                 var rows = statement.executeQuery("SELECT description FROM " + schema + ".farm_financial_transaction WHERE id = 1")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getString(1)).isEqualTo("preserve me");
            }
            try (var connection = connection(); var statement = connection.createStatement();
                 var columns = statement.executeQuery("SELECT column_name FROM information_schema.columns WHERE table_schema = '" + schema + "' AND table_name = 'farm_financial_transaction' AND column_name = 'correction_expected_updated_at'")) {
                assertThat(columns.next()).isFalse();
            }
        } finally {
            execute("DROP SCHEMA " + schema + " CASCADE");
        }
    }

    @Test
    void conflictingIndexRollsBackColumnsAndForeignKeyAddedEarlierInTheMigration() throws Exception {
        String schema = newSchema();
        try {
            execute("CREATE TABLE " + schema + ".farm_financial_transaction (id bigint PRIMARY KEY, description text NOT NULL)");
            execute("CREATE INDEX uq_expense_correction_original ON " + schema + ".farm_financial_transaction(description)");
            execute("INSERT INTO " + schema + ".farm_financial_transaction VALUES (1, 'preserve me')");
            Flyway flyway = flyway(schema);
            flyway.baseline();

            assertThatThrownBy(flyway::migrate).isInstanceOf(FlywayException.class);
            try (var connection = connection(); var statement = connection.createStatement();
                 var columns = statement.executeQuery("SELECT column_name FROM information_schema.columns WHERE table_schema = '" + schema + "' AND table_name = 'farm_financial_transaction' AND column_name IN ('correction_of_id', 'correction_expected_updated_at')")) {
                assertThat(columns.next()).isFalse();
            }
            try (var connection = connection(); var statement = connection.createStatement();
                 var rows = statement.executeQuery("SELECT description FROM " + schema + ".farm_financial_transaction WHERE id = 1")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getString(1)).isEqualTo("preserve me");
            }
        } finally {
            execute("DROP SCHEMA " + schema + " CASCADE");
        }
    }

    private Flyway flyway(String schema) {
        return Flyway.configure().dataSource(url, "poultry_validation", "local-disposable-only")
                .schemas(schema).defaultSchema(schema).baselineVersion("33")
                .locations("classpath:db/reviewed-migration").cleanDisabled(true).load();
    }

    private String newSchema() throws SQLException {
        String schema = "expense_migration_" + UUID.randomUUID().toString().replace("-", "");
        execute("CREATE SCHEMA " + schema);
        return schema;
    }

    private java.sql.Connection connection() throws SQLException {
        return DriverManager.getConnection(url, "poultry_validation", "local-disposable-only");
    }

    private void execute(String sql) throws SQLException {
        try (var connection = connection(); var statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }
}
