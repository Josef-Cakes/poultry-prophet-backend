package com.poultryprophet.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

@EnabledIfEnvironmentVariable(named = "POULTRY_DISPOSABLE_DB_URL", matches = "jdbc:postgresql://127\\.0\\.0\\.1:25432/poultry_prophet_validation")
@SpringBootTest(properties = {"spring.config.import=", "app.offline.command-receipts-enabled=true"})
@AutoConfigureMockMvc
@ActiveProfiles("validation")
class SelectionUpdateReplayIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired JdbcTemplate jdbc;
    @DynamicPropertySource static void isolated(DynamicPropertyRegistry properties) {
        String url = System.getenv("POULTRY_DISPOSABLE_DB_URL");
        if (!"jdbc:postgresql://127.0.0.1:25432/poultry_prophet_validation".equals(url)) throw new IllegalStateException("Disposable DB required before connecting");
        String schema = "selection_receipts_" + UUID.randomUUID().toString().replace("-", "");
        new JdbcTemplate(new DriverManagerDataSource(url, "poultry_validation", "local-disposable-only")).execute("CREATE SCHEMA " + schema);
        String scoped = url + "?currentSchema=" + schema;
        new ResourceDatabasePopulator(new ClassPathResource("db/reviewed-migration/V32__shared_command_receipts.sql"))
                .execute(new DriverManagerDataSource(scoped, "poultry_validation", "local-disposable-only"));
        properties.add("spring.datasource.url", () -> scoped);
        properties.add("spring.datasource.username", () -> "poultry_validation");
        properties.add("spring.datasource.password", () -> "local-disposable-only");
        properties.add("validation.manager.email", () -> "seed-manager@example.test");
        properties.add("validation.manager.password", () -> "seed-password-only");
        properties.add("validation.handler.email", () -> "seed-handler@example.test");
        properties.add("validation.handler.password", () -> "seed-password-only");
        properties.add("poultry.security.jwt-secret", () -> "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=");
    }
    record Fixture(String token, long batch, long session) {}
    private Fixture fixture() throws Exception {
        var auth = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(Map.of(
                "email", UUID.randomUUID()+"@example.test", "password", "test-password-123", "fullName", "Receipt Manager", "farmName", "Receipt Test Farm"))))
                .andReturn().getResponse();
        assertThat(auth.getStatus()).isEqualTo(201);
        JsonNode principal = mapper.readTree(auth.getContentAsString()); String token = principal.path("token").asText();
        String day = LocalDate.now(ZoneId.of("Asia/Manila")).toString();
        var created = mvc.perform(post("/api/batches").header("Authorization", "Bearer "+token).contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("name", "Synthetic receipt batch", "initialPopulation", 10, "bloodline", "Test", "startDate", day, "hatchDate", day, "farmEntryDate", day))))
                .andReturn().getResponse();
        assertThat(created.getStatus()).isEqualTo(201); long batch = mapper.readTree(created.getContentAsString()).path("id").asLong();
        long session = jdbc.queryForObject("INSERT INTO batch_selection_session (farm_id,batch_id,selection_date,reviewer_id,evaluated_count,accepted_count,continue_observation_count,not_accepted_count,other_count,status,source_data_acknowledged,created_at,updated_at,version) "
                + "VALUES (?,?,?,?,10,0,10,0,0,'DRAFT',false,now(),now(),0) RETURNING id", Long.class,
                principal.path("farmId").asLong(), batch, java.sql.Date.valueOf(day), principal.path("userId").asLong());
        return new Fixture(token,batch,session);
    }
    private Map<String,Object> payload(UUID id, int accepted, Long version) {
        var value = new LinkedHashMap<String,Object>(); value.put("operationId",id); value.put("evaluatedCount",10); value.put("acceptedCount",accepted);
        value.put("continueObservationCount",10-accepted); value.put("notAcceptedCount",0); value.put("otherCount",0); value.put("criterionCodes",List.of());
        value.put("sourceDataAcknowledged",false); if(version!=null)value.put("expectedVersion",version); return value;
    }
    private org.springframework.mock.web.MockHttpServletResponse direct(Fixture f, Map<String,Object> payload) throws Exception {
        return mvc.perform(patch("/api/batches/"+f.batch+"/selection-sessions/"+f.session).header("Authorization","Bearer "+f.token)
                .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(payload))).andReturn().getResponse();
    }
    private JsonNode replay(Fixture f, Map<String,Object> payload) throws Exception {
        var data = new LinkedHashMap<>(payload); data.put("sessionId",f.session);
        var response = mvc.perform(post("/api/sync/v2/operations").header("Authorization","Bearer "+f.token).contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("deviceId","receipt-fixture","operations",List.of(Map.of("operationId",payload.get("operationId"),"schemaVersion",1,"entityType","SELECTION_SESSION_UPDATE","batchId",f.batch,"occurredAt",Instant.now().toString(),"payload",data))))))
                .andReturn().getResponse();
        assertThat(response.getStatus()).isEqualTo(200); return mapper.readTree(response.getContentAsString()).path("results").get(0);
    }
    @Test void directAThenBThenOutboxRetryACannotOverwriteBOrFinalization() throws Exception {
        var f=fixture(); var a=payload(UUID.randomUUID(),5,0L); var b=payload(UUID.randomUUID(),6,1L);
        assertThat(direct(f,a).getStatus()).isEqualTo(200); assertThat(direct(f,b).getStatus()).isEqualTo(200);
        assertThat(replay(f,a).path("status").asText()).isEqualTo("ALREADY_APPLIED");
        assertThat(jdbc.queryForObject("SELECT accepted_count FROM batch_selection_session WHERE id=?",Integer.class,f.session)).isEqualTo(6);
        assertThat(jdbc.queryForObject("SELECT version FROM batch_selection_session WHERE id=?",Long.class,f.session)).isEqualTo(2L);
        assertThat(mvc.perform(post("/api/batches/"+f.batch+"/selection-sessions/"+f.session+"/finalize").header("Authorization","Bearer "+f.token)).andReturn().getResponse().getStatus()).isEqualTo(200);
        assertThat(replay(f,a).path("status").asText()).isEqualTo("ALREADY_APPLIED");
        assertThat(jdbc.queryForObject("SELECT status FROM batch_selection_session WHERE id=?",String.class,f.session)).isEqualTo("FINALIZED");
    }
    @Test void unseenStaleAndLegacyCommandsDoNotOverwriteTheDraft() throws Exception {
        var f=fixture(); assertThat(direct(f,payload(UUID.randomUUID(),6,0L)).getStatus()).isEqualTo(200);
        assertThat(replay(f,payload(UUID.randomUUID(),3,0L)).path("status").asText()).isEqualTo("CONFLICT");
        assertThat(replay(f,payload(UUID.randomUUID(),2,null)).path("status").asText()).isEqualTo("CONFLICT");
        assertThat(jdbc.queryForObject("SELECT accepted_count FROM batch_selection_session WHERE id=?",Integer.class,f.session)).isEqualTo(6);
    }
    private JsonNode schedule(Fixture f, UUID id, Map<String,Object> payload) throws Exception {
        var response = mvc.perform(post("/api/sync/v2/operations").header("Authorization","Bearer "+f.token).contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("deviceId","schedule-fixture","operations",List.of(Map.of("operationId",id,"schemaVersion",1,"entityType","VACCINATION_SCHEDULE","batchId",f.batch,"occurredAt","2026-11-22T00:00:00Z","payload",payload))))))
                .andReturn().getResponse();
        assertThat(response.getStatus()).isEqualTo(200); return mapper.readTree(response.getContentAsString()).path("results").get(0);
    }
    private Map<String,Object> schedulePayload(UUID id, String name) {
        return Map.of("operationId",id,"action","CREATE_AND_ASSIGN","replace",false,"program",Map.of("name",name,"items",List.of(Map.of("vaccineName","Fixture vaccine","ageOffsetValue",0,"ageOffsetUnit","DAY","reminderLeadDays",1))));
    }
    @Test void scheduleReplayHasOneProgramAndRejectsChangedPayloadAndBatch() throws Exception {
        var f=fixture(); UUID id=UUID.randomUUID(); var body=schedulePayload(id,"Original program");
        JsonNode applied=schedule(f,id,body); assertThat(applied.path("status").asText()).isEqualTo("APPLIED");
        JsonNode replay=schedule(f,id,body); assertThat(replay.path("status").asText()).isEqualTo("ALREADY_APPLIED");
        assertThat(replay.path("serverId")).isEqualTo(applied.path("serverId"));
        assertThat(replay.path("serverTime")).isEqualTo(applied.path("serverTime"));
        assertThat(schedule(f,id,schedulePayload(id,"Changed program")).path("status").asText()).isEqualTo("CONFLICT");
        var other=new Fixture(f.token,f.batch+10000,f.session);
        assertThat(schedule(other,id,body).path("status").asText()).isEqualTo("CONFLICT");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM vaccination_program WHERE name='Original program'",Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM batch_vaccination_plan_item WHERE batch_id=?",Integer.class,f.batch)).isEqualTo(1);
    }

    @Test void missingSchemaRetainsCommandsWithoutApplyingUpdates() throws Exception {
        var f=fixture(); var body=payload(UUID.randomUUID(),5,0L);
        var ready = mapper.readTree(mvc.perform(get("/api/version")).andReturn().getResponse().getContentAsString()).path("offlineCapabilities");
        assertThat(ready.path("commandReceipts").asBoolean()).isTrue();
        assertThat(ready.path("receiptEntityTypes").toString()).contains("SELECTION_SESSION_UPDATE", "VACCINATION_SCHEDULE");
        jdbc.execute("ALTER TABLE offline_command_receipt RENAME TO held_command_receipt");
        try {
            var unavailable = mapper.readTree(mvc.perform(get("/api/version")).andReturn().getResponse().getContentAsString()).path("offlineCapabilities");
            assertThat(unavailable.path("commandReceipts").asBoolean()).isFalse();
            assertThat(direct(f,body).getStatus()).isEqualTo(503);
            assertThat(replay(f,body).path("status").asText()).isEqualTo("RETRYABLE");
            assertThat(jdbc.queryForObject("SELECT accepted_count FROM batch_selection_session WHERE id=?",Integer.class,f.session)).isZero();
        } finally { jdbc.execute("ALTER TABLE held_command_receipt RENAME TO offline_command_receipt"); }
    }
    @Test void legacyScheduleReceiptCannotBeTurnedIntoAnUnverifiedSuccess() throws Exception {
        var f=fixture(); UUID id=UUID.randomUUID();
        jdbc.update("INSERT INTO offline_sync_receipt(operation_id,farm_id,entity_type,server_id,created_at) VALUES (?,(SELECT farm_id FROM batch WHERE id=?),'VACCINATION_SCHEDULE',123,now())",id,f.batch);
        assertThat(schedule(f,id,schedulePayload(id,"Unverified legacy program")).path("status").asText()).isEqualTo("CONFLICT");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM offline_sync_receipt WHERE operation_id=?",Integer.class,id)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM offline_command_receipt WHERE operation_id=?",Integer.class,id)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM vaccination_program WHERE name='Unverified legacy program'",Integer.class)).isZero();
    }
    @Test void concurrentSelectionCommandSharesOneReceiptAndRevision() throws Exception {
        var f=fixture(); UUID id=UUID.randomUUID(); var body=payload(id,5,0L);
        try(var pool=java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var start=new java.util.concurrent.CountDownLatch(1);
            var first=pool.submit(()->{start.await();return direct(f,body);});
            var second=pool.submit(()->{start.await();return direct(f,body);});
            start.countDown();
            var a=first.get(20,java.util.concurrent.TimeUnit.SECONDS); var b=second.get(20,java.util.concurrent.TimeUnit.SECONDS);
            assertThat(a.getStatus()).isEqualTo(200); assertThat(b.getStatus()).isEqualTo(200);
            assertThat(mapper.readTree(a.getContentAsString())).isEqualTo(mapper.readTree(b.getContentAsString()));
        }
        assertThat(jdbc.queryForObject("SELECT version FROM batch_selection_session WHERE id=?",Long.class,f.session)).isEqualTo(1L);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM offline_command_receipt WHERE operation_id=?",Integer.class,id)).isEqualTo(1);
    }
    @Test void failedScheduleAssignmentRollsBackCreatedProgramAndReceipt() throws Exception {
        var f=fixture(); UUID first=UUID.randomUUID();
        assertThat(schedule(f,first,schedulePayload(first,"Existing assigned program")).path("status").asText()).isEqualTo("APPLIED");
        UUID failed=UUID.randomUUID();
        assertThat(schedule(f,failed,schedulePayload(failed,"Must roll back program")).path("status").asText()).isEqualTo("CONFLICT");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM vaccination_program WHERE name='Must roll back program'",Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM offline_command_receipt WHERE operation_id=?",Integer.class,failed)).isZero();
    }

    @Test void differentConcurrentSelectionEditsCannotBothSpendTheSameRevision() throws Exception {
        var f=fixture();
        try(var pool=java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var start=new java.util.concurrent.CountDownLatch(1);
            var a=pool.submit(()->{start.await();return direct(f,payload(UUID.randomUUID(),5,0L)).getStatus();});
            var b=pool.submit(()->{start.await();return direct(f,payload(UUID.randomUUID(),6,0L)).getStatus();});
            start.countDown();
            assertThat(java.util.List.of(a.get(20,java.util.concurrent.TimeUnit.SECONDS), b.get(20,java.util.concurrent.TimeUnit.SECONDS))).containsExactlyInAnyOrder(200,409);
        }
        assertThat(jdbc.queryForObject("SELECT version FROM batch_selection_session WHERE id=?",Long.class,f.session)).isEqualTo(1L);
    }

    @Test void finalizationChecksTheRevisionReviewedByTheManager() throws Exception {
        var f=fixture(); assertThat(direct(f,payload(UUID.randomUUID(),5,0L)).getStatus()).isEqualTo(200);
        String path="/api/batches/"+f.batch+"/selection-sessions/"+f.session+"/finalize";
        var stale=mvc.perform(post(path).header("Authorization","Bearer "+f.token).contentType(MediaType.APPLICATION_JSON).content("{\"expectedVersion\":0}"))
                .andReturn().getResponse();
        assertThat(stale.getStatus()).isEqualTo(409);
        assertThat(jdbc.queryForObject("SELECT status FROM batch_selection_session WHERE id=?",String.class,f.session)).isEqualTo("DRAFT");
        var applied=mvc.perform(post(path).header("Authorization","Bearer "+f.token).contentType(MediaType.APPLICATION_JSON).content("{\"expectedVersion\":1}"))
                .andReturn().getResponse();
        var replay=mvc.perform(post(path).header("Authorization","Bearer "+f.token).contentType(MediaType.APPLICATION_JSON).content("{\"expectedVersion\":1}"))
                .andReturn().getResponse();
        assertThat(applied.getStatus()).isEqualTo(200); assertThat(replay.getStatus()).isEqualTo(200);
        assertThat(mapper.readTree(applied.getContentAsString())).isEqualTo(mapper.readTree(replay.getContentAsString()));
    }

}
