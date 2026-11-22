package com.poultryprophet.integration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.sql.DriverManager;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Exercises the proposed track in a synthetic schema of the explicitly disposable cluster. */
@EnabledIfEnvironmentVariable(named = "POULTRY_DISPOSABLE_DB_URL",
        matches = "jdbc:postgresql://127\\.0\\.0\\.1:25432/poultry_prophet_validation")
class DisposablePostgresMigrationIntegrationTest {
    @Test
    void explicitBaselineAppliesReviewedMigrationsOnce() throws Exception {
        String url = System.getenv("POULTRY_DISPOSABLE_DB_URL");
        String schema = "migration_check_" + UUID.randomUUID().toString().replace("-", "");
        try (var connection = DriverManager.getConnection(url, "poultry_validation", "local-disposable-only");
             var statement = connection.createStatement()) {
            statement.execute("CREATE SCHEMA " + schema);
            statement.execute("CREATE TABLE " + schema + ".app_user (id bigint PRIMARY KEY)");
            statement.execute("CREATE TABLE " + schema + ".batch_selection_review (id bigint PRIMARY KEY)");
            statement.execute("CREATE TABLE " + schema + ".batch_selection_session (farm_id bigint NOT NULL, batch_id bigint NOT NULL)");
            statement.execute("CREATE TABLE " + schema + ".batch (id bigint PRIMARY KEY, initial_population integer NOT NULL, current_population integer NOT NULL, CONSTRAINT batch_current_population_bounds CHECK (current_population >= 0 AND current_population <= initial_population))");
            statement.execute("CREATE TABLE " + schema + ".handler_task (farm_id bigint NOT NULL, batch_id bigint, source_type varchar(48), status varchar(32) NOT NULL)");
            statement.execute("INSERT INTO " + schema + ".batch (id, initial_population, current_population) VALUES (1, 100, 100)");
        }
        Flyway flyway = Flyway.configure().dataSource(url, "poultry_validation", "local-disposable-only")
                .schemas(schema).defaultSchema(schema).baselineVersion("25")
                .locations("classpath:db/reviewed-migration").cleanDisabled(true).load();
        flyway.baseline();
        assertThat(flyway.migrate().migrationsExecuted).isEqualTo(8);
        assertThat(flyway.migrate().migrationsExecuted).isZero();
        flyway.validate();
        try (var connection = DriverManager.getConnection(url, "poultry_validation", "local-disposable-only");
             var statement = connection.createStatement();
             var result = statement.executeQuery("SELECT relrowsecurity FROM pg_class WHERE oid='" + schema + ".app_user'::regclass")) {
            assertThat(result.next()).isTrue();
            assertThat(result.getBoolean(1)).isTrue();
        }
        try (var connection = DriverManager.getConnection(url, "poultry_validation", "local-disposable-only");
             var statement = connection.createStatement();
             var result = statement.executeQuery("SELECT credential_version FROM " + schema + ".app_user LIMIT 0")) {
            assertThat(result.getMetaData().getColumnName(1)).isEqualTo("credential_version");
        }
        try (var connection = DriverManager.getConnection(url, "poultry_validation", "local-disposable-only");
             var statement = connection.createStatement();
             var result = statement.executeQuery("SELECT selection_review_id, source_data_acknowledged FROM " + schema + ".batch_selection_session LIMIT 0")) {
            assertThat(result.getMetaData().getColumnName(1)).isEqualTo("selection_review_id");
            assertThat(result.getMetaData().getColumnName(2)).isEqualTo("source_data_acknowledged");
        }
        try (var connection = DriverManager.getConnection(url, "poultry_validation", "local-disposable-only");
             var statement = connection.createStatement();
             var result = statement.executeQuery("SELECT derby_date FROM " + schema + ".batch WHERE id = 1")) {
            assertThat(result.next()).isTrue();
            assertThat(result.getDate(1)).isNull();
        }
        try (var connection = DriverManager.getConnection(url, "poultry_validation", "local-disposable-only");
             var statement = connection.createStatement()) {
            assertThat(statement.executeUpdate("UPDATE " + schema + ".batch SET current_population = 120 WHERE id = 1")).isEqualTo(1);
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> statement.executeUpdate("UPDATE " + schema + ".batch SET current_population = -1 WHERE id = 1"))
                    .isInstanceOf(java.sql.SQLException.class);
            statement.executeUpdate("INSERT INTO " + schema + ".handler_task VALUES (1, 1, 'SELECTION_REVIEW_FOLLOWUP', 'TODO')");
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> statement.executeUpdate("INSERT INTO " + schema + ".handler_task VALUES (1, 1, 'SELECTION_REVIEW_FOLLOWUP', 'BLOCKED')"))
                    .isInstanceOf(java.sql.SQLException.class);
            statement.executeUpdate("INSERT INTO " + schema + ".handler_task VALUES (1, 1, 'SELECTION_REVIEW_FOLLOWUP', 'COMPLETED')");
        }
    }
}
