package com.poultryprophet.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
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

/** Opt-in only: this exact address must be a newly created, disposable local cluster. */
@EnabledIfEnvironmentVariable(named = "POULTRY_DISPOSABLE_DB_URL",
        matches = "jdbc:postgresql://127\\.0\\.0\\.1:25432/poultry_prophet_validation")
@SpringBootTest(properties = "spring.config.import=")
@AutoConfigureMockMvc
@ActiveProfiles("validation")
class DisposablePostgresAuthIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;

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
                        "name", "Disposable replay batch", "initialPopulation", 12,
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
