package com.poultryprophet.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;
import java.util.UUID;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;

/** Opt-in only: this exact address must be a newly created, disposable local cluster. */
@EnabledIfEnvironmentVariable(named = "POULTRY_DISPOSABLE_DB_URL",
        matches = "jdbc:postgresql://127\\.0\\.0\\.1:25432/poultry_prophet_validation")
@SpringBootTest(properties = {"spring.config.import=", "app.offline.command-receipts-enabled=true"})
@AutoConfigureMockMvc
@ActiveProfiles("validation")
class DisposablePostgresAuthIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;

    @Test
    void concurrentPopulationRepairsRequireFreshDiagnostics() throws Exception {
        JsonNode manager = register();
        String token = manager.path("token").asText();
        String day = LocalDate.now(ZoneId.of("Asia/Manila")).toString();
        var response = mvc.perform(post("/api/batches").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of(
                        "name", "Disposable reconciliation batch", "initialPopulation", 10, "bloodline", "Test line",
                        "startDate", day, "hatchDate", day, "farmEntryDate", day))))
                .andReturn().getResponse();
        assertThat(response.getStatus()).isEqualTo(201);
        long batchId = json.readTree(response.getContentAsString()).path("id").asLong();
        jdbc.update("UPDATE batch SET current_population = 9 WHERE id = ?", batchId);
        var diagnostics = mvc.perform(get("/api/batches/" + batchId + "/population/diagnostics")
                .header("Authorization", "Bearer " + token)).andReturn().getResponse();
        assertThat(diagnostics.getStatus()).isEqualTo(200);
        String revision = json.readTree(diagnostics.getContentAsString()).path("revision").asText();
        String body = json.writeValueAsString(Map.of("expectedRevision", revision));
        var start = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(2);
        try {
            var a = pool.submit(() -> { start.await(); return mvc.perform(patch("/api/batches/" + batchId + "/population/reconcile")
                    .header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON).content(body)).andReturn().getResponse().getStatus(); });
            var b = pool.submit(() -> { start.await(); return mvc.perform(patch("/api/batches/" + batchId + "/population/reconcile")
                    .header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON).content(body)).andReturn().getResponse().getStatus(); });
            start.countDown();
            assertThat(List.of(a.get(20, TimeUnit.SECONDS), b.get(20, TimeUnit.SECONDS))).containsExactlyInAnyOrder(200, 409);
        } finally { pool.shutdownNow(); }
        assertThat(jdbc.queryForObject("SELECT current_population FROM batch WHERE id = ?", Integer.class, batchId)).isEqualTo(10);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM batch_lifecycle_audit WHERE batch_id = ? AND action = 'POPULATION_RECONCILE'", Integer.class, batchId)).isEqualTo(1);
    }

    @DynamicPropertySource
    static void isolatedDatabase(DynamicPropertyRegistry properties) {
        String url = System.getenv("POULTRY_DISPOSABLE_DB_URL");
        if (!"jdbc:postgresql://127.0.0.1:25432/poultry_prophet_validation".equals(url)) throw new IllegalStateException("Disposable DB required before connecting");
        var ds = new org.springframework.jdbc.datasource.DriverManagerDataSource(url,"poultry_validation","local-disposable-only");
        if (!Boolean.TRUE.equals(new JdbcTemplate(ds).queryForObject("SELECT to_regclass('offline_command_receipt') IS NOT NULL",Boolean.class)))
            new org.springframework.jdbc.datasource.init.ResourceDatabasePopulator(new org.springframework.core.io.ClassPathResource("db/reviewed-migration/V32__shared_command_receipts.sql")).execute(ds);
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
    void reusedRefreshTokenRevokesTheReplacementInCommittedTransaction() throws Exception {
        JsonNode registered = register();
        String original = registered.path("refreshToken").asText();
        var rotated = mvc.perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("refreshToken", original)))).andReturn().getResponse();
        assertThat(rotated.getStatus()).isEqualTo(200);
        String replacement = json.readTree(rotated.getContentAsString()).path("refreshToken").asText();
        assertThat(replacement).isNotBlank().isNotEqualTo(original);
        assertThat(refreshStatus(original)).isEqualTo(400);
        assertThat(refreshStatus(replacement)).isEqualTo(400);
    }

    @Test
    void changingPasswordInvalidatesAccessAndRefreshCredentials() throws Exception {
        JsonNode registered = register();
        String token = registered.path("token").asText();
        String refresh = registered.path("refreshToken").asText();
        var change = mvc.perform(put("/api/account/password").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of(
                        "currentPassword", "test-password-123", "newPassword", "changed-password-456"))))
                .andReturn().getResponse();
        assertThat(change.getStatus()).isEqualTo(204);
        int oldAccess = mvc.perform(get("/api/farm").header("Authorization", "Bearer " + token))
                .andReturn().getResponse().getStatus();
        assertThat(oldAccess).isIn(401, 403);
        assertThat(refreshStatus(refresh)).isEqualTo(400);
    }

    @Test
    void reusingAnEmailDoesNotMoveAnOldAccessTokenToTheNewAccount() throws Exception {
        String oldEmail = "reused-" + UUID.randomUUID() + "@example.test";
        JsonNode original = register(oldEmail);
        String oldToken = original.path("token").asText();
        var update = mvc.perform(put("/api/account/profile").header("Authorization", "Bearer " + oldToken)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of(
                        "email", "moved-" + UUID.randomUUID() + "@example.test", "fullName", "Original Manager"))))
                .andReturn().getResponse();
        assertThat(update.getStatus()).isEqualTo(200);
        JsonNode replacement = register(oldEmail);
        assertThat(replacement.path("userId").asLong()).isNotEqualTo(original.path("userId").asLong());
        var farmResponse = mvc.perform(get("/api/farm").header("Authorization", "Bearer " + oldToken))
                .andReturn().getResponse();
        assertThat(farmResponse.getStatus()).isEqualTo(200);
        assertThat(json.readTree(farmResponse.getContentAsString()).path("id").asLong())
                .isEqualTo(original.path("farmId").asLong()).isNotEqualTo(replacement.path("farmId").asLong());
    }

    @Test
    void dailyReplayIsIdempotentAndConflictRollsBackReceipt() throws Exception {
        JsonNode registered = register();
        String token = registered.path("token").asText();
        String day = LocalDate.now(ZoneId.of("Asia/Manila")).toString();
        var batchResponse = mvc.perform(post("/api/batches").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of(
                        "name", "Disposable replay batch", "initialPopulation", 12, "bloodline", "Test line",
                        "startDate", day, "hatchDate", day, "farmEntryDate", day))))
                .andReturn().getResponse();
        assertThat(batchResponse.getStatus()).isEqualTo(201);
        long batchId = json.readTree(batchResponse.getContentAsString()).path("id").asLong();
        UUID operationId = UUID.randomUUID();
        String timestamp = Instant.now().toString();
        Map<String, Object> payload = Map.of("operationId", operationId.toString(),
                "ownerUserId", registered.path("userId").asLong(), "ownerFarmId", registered.path("farmId").asLong(),
                "recordDate", day, "temperatureC", 31.0);
        var pool = Executors.newFixedThreadPool(2);
        var start = new CountDownLatch(1);
        try {
            var first = pool.submit(() -> { start.await(); return syncStatus(token, batchId, operationId, timestamp, payload); });
            var second = pool.submit(() -> { start.await(); return syncStatus(token, batchId, operationId, timestamp, payload); });
            start.countDown();
            assertThat(List.of(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder("APPLIED", "ALREADY_APPLIED");
        } finally {
            pool.shutdownNow();
        }
        UUID conflicting = UUID.randomUUID();
        Map<String, Object> later = Map.of("operationId", conflicting.toString(),
                "ownerUserId", registered.path("userId").asLong(), "ownerFarmId", registered.path("farmId").asLong(),
                "recordDate", day, "temperatureC", 32.0);
        assertThat(syncStatus(token, batchId, conflicting, timestamp, later)).isEqualTo("CONFLICT");
        Integer receipts = jdbc.queryForObject("select count(*) from offline_daily_record_receipt where operation_id = ?",
                Integer.class, conflicting);
        assertThat(receipts).isZero();
    }

    @Test
    void paidStockInCreatesExactlyOneMovementAndExpenseOnReplay() throws Exception {
        JsonNode registered = register();
        String token = registered.path("token").asText();
        var productResponse = mvc.perform(post("/api/inventory/products").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of(
                        "productType", "FEED", "brandName", "Disposable feed", "stockUnit", "kg"))))
                .andReturn().getResponse();
        assertThat(productResponse.getStatus()).isEqualTo(201);
        long productId = json.readTree(productResponse.getContentAsString()).path("id").asLong();
        UUID operationId = UUID.randomUUID();
        Map<String, Object> purchase = Map.of("quantity", 10, "purchaseUnitCost", 3.5,
                "supplier", "Synthetic supplier", "operationId", operationId.toString());
        var first = mvc.perform(post("/api/inventory/products/{id}/stock-ins", productId)
                .header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(purchase))).andReturn().getResponse();
        assertThat(first.getStatus()).isEqualTo(200);
        var replay = mvc.perform(post("/api/inventory/products/{id}/stock-ins", productId)
                .header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(purchase))).andReturn().getResponse();
        assertThat(replay.getStatus()).isEqualTo(200);
        assertThat(json.readTree(first.getContentAsString()).path("id").asLong())
                .isEqualTo(json.readTree(replay.getContentAsString()).path("id").asLong());
        JsonNode products = json.readTree(mvc.perform(get("/api/inventory/products")
                .header("Authorization", "Bearer " + token)).andReturn().getResponse().getContentAsString());
        assertThat(products.get(0).path("stockOnHand").decimalValue()).isEqualByComparingTo("10");
        JsonNode transactions = json.readTree(mvc.perform(get("/api/financial-transactions")
                .header("Authorization", "Bearer " + token)).andReturn().getResponse().getContentAsString());
        assertThat(transactions.size()).isEqualTo(1);
        assertThat(transactions.get(0).path("amount").decimalValue()).isEqualByComparingTo("35.00");
    }

    @Test
    void healthDeathApiValidatesObservationsAndPreservesPopulationOnRejectedInput() throws Exception {
        String token = register().path("token").asText();
        String day = LocalDate.now(ZoneId.of("Asia/Manila")).toString();
        long batchId = createDeathObservationBatch(token, day);
        String firstOperation = UUID.randomUUID().toString();
        Map<String, Object> emptySigns = deathPayload(day, firstOperation, "SIGNS", "", "Signs noticed: ");

        assertThat(postDeath(token, batchId, emptySigns)).isEqualTo(400);
        assertThat(currentPopulation(batchId)).isEqualTo(10);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM batch_event WHERE batch_id = ?", Integer.class, batchId)).isZero();

        Map<String, Object> signs = deathPayload(day, firstOperation, "SIGNS", "coughing", "Signs noticed: coughing");
        assertThat(postDeath(token, batchId, signs)).isEqualTo(201);
        assertThat(postDeath(token, batchId, signs)).isEqualTo(201);
        assertThat(postDeath(token, batchId, deathPayload(day, UUID.randomUUID().toString(), "NO_SIGNS", "", "No signs noticed."))).isEqualTo(201);
        assertThat(postDeath(token, batchId, deathPayload(day, UUID.randomUUID().toString(), "UNKNOWN", "", "Signs not observed / unknown."))).isEqualTo(201);
        assertThat(currentPopulation(batchId)).isEqualTo(7);

        var preview = mvc.perform(get("/api/batches/{id}/selection-review/preview", batchId)
                .header("Authorization", "Bearer " + token)).andReturn().getResponse();
        assertThat(preview.getStatus()).isEqualTo(200);
        assertThat(preview.getContentAsString()).contains("Signs noticed: coughing", "No signs noticed.", "Signs not observed / unknown.");
    }

    @Test
    void offlineDeathSyncPreservesChoiceAndAllowsCorrectionWithSameOperationId() throws Exception {
        String token = register().path("token").asText();
        String day = LocalDate.now(ZoneId.of("Asia/Manila")).toString();
        long batchId = createDeathObservationBatch(token, day);
        String operationId = UUID.randomUUID().toString();
        Map<String, Object> bad = deathPayload(day, operationId, "SIGNS", "", "Signs noticed: \nOptional note");
        bad.remove("eventDate"); // Exercises date derivation during sync.
        assertThat(syncDeath(token, batchId, operationId, bad)).isEqualTo("REJECTED");
        assertThat(currentPopulation(batchId)).isEqualTo(10);

        Map<String, Object> fixed = deathPayload(day, operationId, "SIGNS", "weak", "Signs noticed: weak\nOptional note");
        fixed.remove("eventDate");
        assertThat(syncDeath(token, batchId, operationId, fixed)).isEqualTo("APPLIED");
        assertThat(syncDeath(token, batchId, operationId, fixed)).isEqualTo("ALREADY_APPLIED");
        for (String observation : List.of("NO_SIGNS", "UNKNOWN")) {
            String id = UUID.randomUUID().toString();
            String details = observation.equals("NO_SIGNS") ? "No signs noticed." : "Signs not observed / unknown.";
            assertThat(syncDeath(token, batchId, id, deathPayload(day, id, observation, "", details))).isEqualTo("APPLIED");
        }
        assertThat(currentPopulation(batchId)).isEqualTo(7);
    }

    @Test
    void inputNumericReplayAfterDatabaseRoundTripHasOneBusinessRecord() throws Exception {
        String token = register().path("token").asText();
        long batch = createDeathObservationBatch(token, LocalDate.now(ZoneId.of("Asia/Manila")).toString());
        var productResponse = mvc.perform(post("/api/inventory/products").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of(
                        "productType", "FEED", "brandName", "Replay stock", "stockUnit", "kg"))))
                .andReturn().getResponse();
        assertThat(productResponse.getStatus()).isEqualTo(201);
        long product = json.readTree(productResponse.getContentAsString()).path("id").asLong();
        assertThat(mvc.perform(post("/api/inventory/products/{id}/stock-ins", product)
                .header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("quantity", 10, "purchaseUnitCost", 3.5, "operationId", UUID.randomUUID().toString()))))
                .andReturn().getResponse().getStatus()).isEqualTo(200);
        String id = UUID.randomUUID().toString();
        var payload = new java.util.HashMap<String, Object>();
        payload.put("batchId", batch); payload.put("operationId", id);
        payload.put("productType", "FEED"); payload.put("brandName", "Replay fixture");
        payload.put("farmProductId", product); payload.put("unit", "kg");
        payload.put("quantity", 1); payload.put("recordedAt", Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MILLIS).toString());
        var first = postInput(token, payload);
        assertThat(first.getStatus()).isEqualTo(201);
        long serverId = json.readTree(first.getContentAsString()).path("id").asLong();
        for (var quantity : List.of(new java.math.BigDecimal("1"), new java.math.BigDecimal("1.0"), new java.math.BigDecimal("1.000"))) {
            payload.put("quantity", quantity);
            var replay = postInput(token, payload);
            assertThat(replay.getStatus()).isEqualTo(201);
            assertThat(json.readTree(replay.getContentAsString()).path("id").asLong()).isEqualTo(serverId);
        }
        payload.put("quantity", 2);
        assertThat(postInput(token, payload).getStatus()).isEqualTo(409);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM farm_input_log WHERE operation_id = ?::uuid", Integer.class, id)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT stock_on_hand FROM farm_product WHERE id = ?", java.math.BigDecimal.class, product)).isEqualByComparingTo("9");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM inventory_movement WHERE farm_input_log_id = ?", Integer.class, serverId)).isEqualTo(1);
    }

    @Test
    void legacyQueuedInputWithoutTimestampReplaysOriginalServerRecord() throws Exception {
        String token = register().path("token").asText();
        long batch = createDeathObservationBatch(token, LocalDate.now(ZoneId.of("Asia/Manila")).toString());
        String id = UUID.randomUUID().toString();
        var payload = Map.<String, Object>of("batchId", batch, "operationId", id, "productType", "FEED", "brandName", "Legacy timestamp fixture");
        var first = postInput(token, payload);
        assertThat(first.getStatus()).isEqualTo(201);
        var original = json.readTree(first.getContentAsString());
        var operation = Map.of("operationId", id, "schemaVersion", 1, "entityType", "FARM_INPUT", "batchId", batch,
                "occurredAt", Instant.now().plusSeconds(30).toString(), "payload", payload);
        var replay = mvc.perform(post("/api/sync/v2/operations").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("operations", List.of(operation)))))
                .andReturn().getResponse();
        assertThat(replay.getStatus()).isEqualTo(200);
        var result = json.readTree(replay.getContentAsString()).path("results").get(0);
        assertThat(result.path("status").asText()).isEqualTo("ALREADY_APPLIED");
        assertThat(result.path("serverId").asLong()).isEqualTo(original.path("id").asLong());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM farm_input_log WHERE operation_id = ?::uuid", Integer.class, id)).isEqualTo(1);
    }

    private org.springframework.mock.web.MockHttpServletResponse postInput(String token, Map<String, Object> body) throws Exception {
        return mvc.perform(post("/api/inputs").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body))).andReturn().getResponse();
    }

    private long createDeathObservationBatch(String token, String day) throws Exception {
        var response = mvc.perform(post("/api/batches").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of(
                        "name", "Disposable observation test", "initialPopulation", 10, "bloodline", "Test line",
                        "startDate", day, "hatchDate", day, "farmEntryDate", day))))
                .andReturn().getResponse();
        assertThat(response.getStatus()).isEqualTo(201);
        return json.readTree(response.getContentAsString()).path("id").asLong();
    }

    private int currentPopulation(long batchId) {
        return jdbc.queryForObject("SELECT current_population FROM batch WHERE id = ?", Integer.class, batchId);
    }

    private Map<String, Object> deathPayload(String day, String operationId, String observation, String signs, String details) {
        var payload = new java.util.HashMap<String, Object>();
        payload.put("eventDate", day);
        payload.put("eventType", "HEALTH_DEATH");
        payload.put("title", "Health-related death");
        payload.put("affectedCount", 1);
        payload.put("operationId", operationId);
        payload.put("deathObservation", observation);
        payload.put("deathSigns", signs);
        payload.put("details", details);
        return payload;
    }

    private int postDeath(String token, long batchId, Map<String, Object> payload) throws Exception {
        return mvc.perform(post("/api/batches/{id}/events", batchId).header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(payload)))
                .andReturn().getResponse().getStatus();
    }

    private String syncDeath(String token, long batchId, String operationId, Map<String, Object> payload) throws Exception {
        Instant recordedAt = LocalDate.now(ZoneId.of("Asia/Manila")).atStartOfDay(ZoneId.of("Asia/Manila")).toInstant();
        Map<String, Object> operation = Map.of("operationId", operationId, "schemaVersion", 1,
                "entityType", "BATCH_EVENT", "batchId", batchId, "occurredAt", recordedAt.toString(), "payload", payload);
        var response = mvc.perform(post("/api/sync/v2/operations").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of(
                        "deviceId", "disposable-observation-test", "operations", List.of(operation)))))
                .andReturn().getResponse();
        assertThat(response.getStatus()).isEqualTo(200);
        return json.readTree(response.getContentAsString()).path("results").get(0).path("status").asText();
    }

    private String syncStatus(String token, long batchId, UUID id, String occurredAt, Map<String, Object> payload) throws Exception {
        Map<String, Object> operation = Map.of("operationId", id.toString(), "schemaVersion", 1,
                "entityType", "DAILY_RECORD", "batchId", batchId, "occurredAt", occurredAt, "payload", payload);
        var response = mvc.perform(post("/api/sync/v2/operations").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of(
                        "deviceId", "disposable-test", "operations", List.of(operation)))))
                .andReturn().getResponse();
        assertThat(response.getStatus()).isEqualTo(200);
        return json.readTree(response.getContentAsString()).path("results").get(0).path("status").asText();
    }

    private JsonNode register() throws Exception {
        return register("validation-" + UUID.randomUUID() + "@example.test");
    }

    private JsonNode register(String email) throws Exception {
        var response = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("email", email, "password", "test-password-123",
                        "fullName", "Disposable Test Manager")))).andReturn().getResponse();
        assertThat(response.getStatus()).isEqualTo(201);
        return json.readTree(response.getContentAsString());
    }

    private int refreshStatus(String token) throws Exception {
        return mvc.perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("refreshToken", token))))
                .andReturn().getResponse().getStatus();
    }
}
