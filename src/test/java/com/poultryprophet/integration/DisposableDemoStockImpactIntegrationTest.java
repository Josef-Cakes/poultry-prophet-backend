package com.poultryprophet.integration;

import com.poultryprophet.inventory.InventoryMovementRepository;
import com.poultryprophet.inventory.InventoryMovementType;
import com.poultryprophet.finance.FinanceService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.sql.Timestamp;

import static org.assertj.core.api.Assertions.assertThat;

/** Runs only against the explicitly disposable local validation database. */
@EnabledIfEnvironmentVariable(named = "POULTRY_DISPOSABLE_DB_URL",
        matches = "jdbc:postgresql://127\\.0\\.0\\.1:25432/poultry_prophet_validation")
@SpringBootTest(properties = "spring.config.import=")
@ActiveProfiles("validation")
@Transactional
class DisposableDemoStockImpactIntegrationTest {
    @Autowired InventoryMovementRepository movements;
    @Autowired JdbcTemplate jdbc;
    @Autowired FinanceService finance;

    @DynamicPropertySource
    static void isolatedDatabase(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", () -> System.getenv("POULTRY_DISPOSABLE_DB_URL"));
        properties.add("spring.datasource.username", () -> "poultry_validation");
        properties.add("spring.datasource.password", () -> "local-disposable-only");
        properties.add("poultry.security.jwt-secret", () -> "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=");
        properties.add("validation.manager.email", () -> "seed-manager@example.test");
        properties.add("validation.manager.password", () -> "seed-password-only");
        properties.add("validation.handler.email", () -> "seed-handler@example.test");
        properties.add("validation.handler.password", () -> "seed-password-only");
    }

    @Test
    void flagsOnlyUsesAfterMarkedStockWithinFarmReportPeriodAndSnapshotCutoff() {
        long farm = 932111L;
        long product = 932112L;
        long batch = 932113L;
        jdbc.update("""
            insert into farm_product (id, farm_id, product_type, brand_name, stock_unit, stock_on_hand,
              valuation_status, allow_fractional_quantity, active, version, created_by, created_at, updated_at)
            values (?, ?, 'FEED', 'demo fixture', 'pack', 10, 'VALUED', true, true, 0, 1, now(), now())
            """, product, farm);
        insertMovement(farm, product, batch, "USAGE", -1, "2026-11-05T00:00:00Z", "2026-11-05T00:00:00Z", null);
        insertMovement(farm, product, null, "STOCK_IN", 50, "2026-11-08T00:00:00Z", "2026-11-10T00:00:00Z", "DEMO / TEST DATA; simulated purchase");
        insertMovement(farm, product, batch, "USAGE", -1, "2026-11-06T00:00:00Z", "2026-11-11T00:00:00Z", null);
        Instant start = Instant.parse("2026-11-01T00:00:00Z");
        Instant end = Instant.parse("2026-12-01T00:00:00Z");

        assertThat(movements.findProductsWithDemoStockHistory(farm, InventoryMovementType.STOCK_IN)).contains(product);
        assertThat(movements.findProductsWithDemoStockHistory(farm + 1, InventoryMovementType.STOCK_IN)).isEmpty();
        assertThat(impact(farm, batch, start, end, "2026-11-09T00:00:00Z")).isFalse();
        assertThat(impact(farm, batch, start, end, "2026-11-12T00:00:00Z")).isTrue();
        assertThat(impact(farm + 1, batch, start, end, "2026-11-12T00:00:00Z")).isFalse();
        assertThat(impact(farm, batch + 1, start, end, "2026-11-12T00:00:00Z")).isFalse();
        assertThat(impact(farm, batch, Instant.parse("2026-11-07T00:00:00Z"), end, "2026-11-12T00:00:00Z")).isFalse();
    }

    @Test
    void reportsOnlyPostedDemoAmountsWithinFarmDateAndCurrencyFilters() {
        long farm = 932211L;
        insertTransaction(farm, "2026-11-10", "PHP", "POSTED", 3250, "DEMO / TEST DATA; simulated purchase");
        insertTransaction(farm, "2026-11-10", "PHP", "POSTED", 50, "A real purchase");
        insertTransaction(farm, "2026-11-10", "PHP", "VOIDED", 900, "DEMO / TEST DATA; voided");
        insertTransaction(farm, "2026-10-10", "PHP", "POSTED", 700, "DEMO / TEST DATA; earlier");
        insertTransaction(farm, "2026-11-10", "USD", "POSTED", 20, "DEMO / TEST DATA; simulated purchase");
        insertTransaction(farm + 1, "2026-11-10", "PHP", "POSTED", 600, "DEMO / TEST DATA; another farm");
        var result = finance.analytics(farm, null, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30));
        var php = result.byCurrency().stream().filter(report -> report.currency().equals("PHP")).findFirst().orElseThrow();
        var usd = result.byCurrency().stream().filter(report -> report.currency().equals("USD")).findFirst().orElseThrow();
        assertThat(php.demoTransactionCount()).isEqualTo(1);
        assertThat(php.demoExpenseAmount()).isEqualByComparingTo("3250");
        assertThat(php.totals().recordedExpense()).isEqualByComparingTo("3300");
        assertThat(usd.demoTransactionCount()).isEqualTo(1);
        assertThat(usd.demoExpenseAmount()).isEqualByComparingTo("20");
        var outside = finance.analytics(farm, null, LocalDate.of(2026, 12, 1), LocalDate.of(2026, 12, 31));
        assertThat(outside.byCurrency().getFirst().demoTransactionCount()).isZero();
    }

    private void insertTransaction(long farm, String date, String currency, String status, int amount, String description) {
        jdbc.update("""
            insert into farm_financial_transaction (farm_id, transaction_date, type, category, amount, currency,
              source_type, status, description, entered_by, created_at, updated_at)
            values (?, ?, 'EXPENSE', 'Feed', ?, ?, 'INVENTORY_PURCHASE', ?, ?, 1, now(), now())
            """, farm, java.sql.Date.valueOf(date), amount, currency, status, description);
    }

    private void insertMovement(long farm, long product, Long batch, String type, int delta, String occurred, String created, String reason) {
        jdbc.update("""
            insert into inventory_movement (farm_id, farm_product_id, batch_id, movement_type, quantity_delta,
                balance_after, occurred_at, created_at, reason, recorded_by)
            values (?, ?, ?, ?, ?, 10, ?, ?, ?, 1)
            """, farm, product, batch, type, delta, Timestamp.from(Instant.parse(occurred)), Timestamp.from(Instant.parse(created)), reason);
    }

    private boolean impact(long farm, long batch, Instant start, Instant end, String cutoff) {
        return movements.existsUseAfterDemoStock(farm, batch, start, end, Instant.parse(cutoff),
                InventoryMovementType.USAGE, InventoryMovementType.STOCK_IN);
    }
}
