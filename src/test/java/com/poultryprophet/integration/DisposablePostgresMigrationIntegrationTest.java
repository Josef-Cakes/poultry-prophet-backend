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
        }
        Flyway flyway = Flyway.configure().dataSource(url, "poultry_validation", "local-disposable-only")
                .schemas(schema).defaultSchema(schema).baselineVersion("25")
                .locations("classpath:db/reviewed-migration").cleanDisabled(true).load();
        flyway.baseline();
        assertThat(flyway.migrate().migrationsExecuted).isEqualTo(2);
        assertThat(flyway.migrate().migrationsExecuted).isZero();
        flyway.validate();
        try (var connection = DriverManager.getConnection(url, "poultry_validation", "local-disposable-only");
             var statement = connection.createStatement();
             var result = statement.executeQuery("SELECT credential_version FROM " + schema + ".app_user LIMIT 0")) {
            assertThat(result.getMetaData().getColumnName(1)).isEqualTo("credential_version");
        }
    }
}
