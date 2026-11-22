package com.poultryprophet.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.poultryprophet.common.ConflictException;
import com.poultryprophet.sync.CommandReceiptService;
import com.poultryprophet.sync.CommandReceiptService.CommandIdentity;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;

@EnabledIfEnvironmentVariable(named = "POULTRY_DISPOSABLE_DB_URL",
        matches = "jdbc:postgresql://127\\.0\\.0\\.1:25432/poultry_prophet_validation")
class CommandReceiptIntegrationTest {
    private JdbcTemplate jdbc;
    private CommandReceiptService service;
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    public record Outcome(long serverId, Instant appliedAt) {}
    private static final Instant APPLIED = Instant.parse("2026-11-22T00:00:00.123456789Z");

    @BeforeEach void setup() {
        String url = System.getenv("POULTRY_DISPOSABLE_DB_URL");
        if (!"jdbc:postgresql://127.0.0.1:25432/poultry_prophet_validation".equals(url)) throw new IllegalStateException("Disposable DB required before connecting");
        var admin = new DriverManagerDataSource(url, "poultry_validation", "local-disposable-only");
        String schema = "command_receipt_" + UUID.randomUUID().toString().replace("-", "");
        new JdbcTemplate(admin).execute("CREATE SCHEMA " + schema);
        var ds = new DriverManagerDataSource(url + "?currentSchema=" + schema, "poultry_validation", "local-disposable-only");
        var beforeMigration = new JdbcTemplate(ds);
        beforeMigration.execute("DO $$ BEGIN IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname='anon') THEN CREATE ROLE anon NOLOGIN; END IF; IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname='authenticated') THEN CREATE ROLE authenticated NOLOGIN; END IF; END $$");
        beforeMigration.execute("GRANT USAGE ON SCHEMA " + schema + " TO anon, authenticated");
        beforeMigration.execute("ALTER DEFAULT PRIVILEGES IN SCHEMA " + schema + " GRANT ALL ON TABLES TO anon, authenticated");
        new ResourceDatabasePopulator(new ClassPathResource("db/reviewed-migration/V32__shared_command_receipts.sql")).execute(ds);
        jdbc = new JdbcTemplate(ds);
        jdbc.execute("CREATE TABLE effect (id bigserial PRIMARY KEY, value text NOT NULL)");
        service = new CommandReceiptService(jdbc, mapper, new DataSourceTransactionManager(ds), true);
    }
    private CommandIdentity identity(UUID id) { return new CommandIdentity(id, 1, 10L, 20L, "SELECTION_SESSION_UPDATE", "SELECTION_SESSION", 30L); }
    private Outcome effect() { return new Outcome(jdbc.queryForObject("INSERT INTO effect(value) VALUES ('A') RETURNING id", Long.class), APPLIED); }
    private CommandReceiptService.Execution<Outcome> apply(CommandIdentity identity, String json) throws Exception {
        return service.executeOnce(identity, mapper.readTree(json), Outcome.class, this::effect);
    }

    @Test void receiptPayloadsAreNotExposedToBrowserDatabaseRoles() throws Exception {
        assertThat(jdbc.queryForObject("SELECT relrowsecurity FROM pg_class WHERE oid=to_regclass('offline_command_receipt')",Boolean.class)).isTrue();
        apply(identity(UUID.randomUUID()), "{}");
        for (String role : java.util.List.of("anon", "authenticated")) {
            try (var connection = jdbc.getDataSource().getConnection(); var statement = connection.createStatement()) {
                // Both roles deliberately inherited default table grants in this fixture.
                statement.execute("SET ROLE " + role);
                try (var rows = statement.executeQuery("SELECT count(*) FROM offline_command_receipt")) {
                    assertThat(rows.next()).isTrue(); assertThat(rows.getInt(1)).isZero();
                }
                assertThatThrownBy(() -> statement.executeUpdate("INSERT INTO offline_command_receipt(operation_id,schema_version,farm_id,user_id,entity_type,resource_type,resource_id,payload) VALUES ('00000000-0000-4000-8000-000000000001',1,10,20,'TEST','BATCH',30,'{}')"))
                        .isInstanceOf(java.sql.SQLException.class).hasMessageContaining("row-level security");
            }
        }
    }
    @Test void missingUniqueIdentityConstraintCannotRunBusinessWork() throws Exception {
        jdbc.execute("ALTER TABLE offline_command_receipt DROP CONSTRAINT offline_command_receipt_pkey");
        assertThatThrownBy(() -> apply(identity(UUID.randomUUID()), "{}"))
                .isInstanceOf(com.poultryprophet.sync.CommandReceiptsUnavailableException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM effect", Integer.class)).isZero();
    }
    @Test void missingReceiptTableCannotRunBusinessWork() throws Exception {
        jdbc.execute("DROP TABLE offline_command_receipt");
        assertThatThrownBy(() -> apply(identity(UUID.randomUUID()), "{}"))
                .isInstanceOf(com.poultryprophet.sync.CommandReceiptsUnavailableException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM effect", Integer.class)).isZero();
    }
    @Test void concurrentSameCommandProducesOneEffectAndOriginalOutcome() throws Exception {
        UUID id = UUID.randomUUID(); var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var first = pool.submit(() -> service.executeOnce(identity(id), mapper.readTree("{\"quantity\":1}"), Outcome.class, () -> {
                entered.countDown();
                try { if (!release.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("fixture timed out"); }
                catch (InterruptedException ex) { throw new IllegalStateException(ex); }
                return effect();
            }));
            assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
            var second = pool.submit(() -> apply(identity(id), "{\"quantity\":1.000}"));
            try { assertThatThrownBy(() -> second.get(150, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class); }
            finally { release.countDown(); }
            var a = first.get(5, TimeUnit.SECONDS); var b = second.get(5, TimeUnit.SECONDS);
            assertThat(a.replayed()).isFalse(); assertThat(b.replayed()).isTrue(); assertThat(b.outcome()).isEqualTo(a.outcome());
            assertThat(jdbc.queryForObject("SELECT count(*) FROM effect", Integer.class)).isEqualTo(1);
        }
    }
    @Test void changedPayloadActorFarmTypeResourceOrSchemaNeverBorrowsSuccess() throws Exception {
        UUID id = UUID.randomUUID(); apply(identity(id), "{\"quantity\":1}");
        assertThatThrownBy(() -> apply(identity(id), "{\"quantity\":2}")).isInstanceOf(ConflictException.class);
        for (var changed : java.util.List.of(
                new CommandIdentity(id, 1, 11L, 20L, "SELECTION_SESSION_UPDATE", "SELECTION_SESSION", 30L),
                new CommandIdentity(id, 1, 10L, 21L, "SELECTION_SESSION_UPDATE", "SELECTION_SESSION", 30L),
                new CommandIdentity(id, 2, 10L, 20L, "SELECTION_SESSION_UPDATE", "SELECTION_SESSION", 30L),
                new CommandIdentity(id, 1, 10L, 20L, "OTHER", "SELECTION_SESSION", 30L),
                new CommandIdentity(id, 1, 10L, 20L, "SELECTION_SESSION_UPDATE", "OTHER", 30L),
                new CommandIdentity(id, 1, 10L, 20L, "SELECTION_SESSION_UPDATE", "SELECTION_SESSION", 31L))) {
            assertThatThrownBy(() -> apply(changed, "{\"quantity\":1}")).isInstanceOf(ConflictException.class);
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM effect", Integer.class)).isEqualTo(1);
    }
    @Test void failedWorkRollsBackReservationAndBusinessWritesThenCanRetry() throws Exception {
        UUID id = UUID.randomUUID();
        assertThatThrownBy(() -> service.executeOnce(identity(id), mapper.createObjectNode(), Outcome.class, () -> {
            effect(); throw new IllegalStateException("rollback fixture");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM offline_command_receipt", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM effect", Integer.class)).isZero();
        assertThat(apply(identity(id), "{}").replayed()).isFalse();
    }
    @Test void lostReplyReturnsImmutableOutcomeAfterLaterEditAndObjectKeyReordering() throws Exception {
        UUID id = UUID.randomUUID(); var a = apply(identity(id), "{\"notes\":\"original\",\"quantity\":1}");
        jdbc.update("UPDATE effect SET value = 'later edit'");
        var replay = apply(identity(id), "{\"quantity\":1.0,\"notes\":\"original\"}");
        assertThat(replay.replayed()).isTrue(); assertThat(replay.outcome()).isEqualTo(a.outcome());
        assertThat(replay.outcome().appliedAt()).isEqualTo(APPLIED);
        assertThat(jdbc.queryForObject("SELECT value FROM effect", String.class)).isEqualTo("later edit");
    }
    @Test void orphanedLegacyLedgerEvidenceCannotBeReappliedAsANewCommand() throws Exception {
        jdbc.execute("CREATE TABLE inventory_movement(operation_id uuid)");
        jdbc.execute("CREATE TABLE farm_financial_transaction(source_operation_id varchar(80))");
        UUID stock=UUID.randomUUID(),sale=UUID.randomUUID();
        jdbc.update("INSERT INTO inventory_movement VALUES (?)",stock);
        jdbc.update("INSERT INTO farm_financial_transaction VALUES (?)",sale.toString());
        for(UUID id:java.util.List.of(stock,sale)) assertThatThrownBy(()->apply(identity(id),"{}"))
                .isInstanceOf(ConflictException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM effect",Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM offline_command_receipt",Integer.class)).isZero();
    }

}
