package com.poultryprophet.inventory;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class InventoryValuationMigrationContractTest {
    @Test
    void v14BackfillsBeforeEnforcingNonNullValuationStatus() throws IOException {
        String migration = read("/db/migration/V14__inventory_cost_valuation.sql");
        String normalized = migration.toLowerCase();

        assertThat(normalized).contains(
                "add column if not exists valuation_status varchar(16);",
                "set valuation_status = case",
                "where valuation_status is null;",
                "alter column valuation_status set default 'unvalued';",
                "alter column valuation_status set not null;"
        );
    }

    @Test
    void schemaBootstrapMatchesTheStagedValuationRepair() throws IOException {
        String schema = read("/schema.sql").toLowerCase();

        assertThat(schema).contains(
                "add column if not exists valuation_status varchar(16);",
                "set valuation_status = case",
                "alter column valuation_status set default 'unvalued';",
                "alter column valuation_status set not null;",
                "ck_farm_product_valuation_status"
        );
    }

    private static String read(String resource) throws IOException {
        try (InputStream input = InventoryValuationMigrationContractTest.class.getResourceAsStream(resource)) {
            assertThat(input).as("resource %s must be available", resource).isNotNull();
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
