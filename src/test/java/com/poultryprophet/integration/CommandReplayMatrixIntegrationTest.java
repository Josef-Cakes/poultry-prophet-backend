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
class CommandReplayMatrixIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired JdbcTemplate jdbc;
    @Autowired org.springframework.context.ApplicationContext context;
    @DynamicPropertySource static void isolated(DynamicPropertyRegistry properties) {
        String url = System.getenv("POULTRY_DISPOSABLE_DB_URL");
        if (!"jdbc:postgresql://127.0.0.1:25432/poultry_prophet_validation".equals(url)) throw new IllegalStateException("Disposable DB required before connecting");
        String schema = "remaining_receipts_" + UUID.randomUUID().toString().replace("-", "");
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
    record Fixture(String token,long farm,long user,long batch,String day) {}
    private Fixture fixture() throws Exception {
        var response=mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(Map.of("email",UUID.randomUUID()+"@example.test","password","test-password-123","fullName","Replay Manager","farmName","Synthetic Replay Farm")))).andReturn().getResponse();
        assertThat(response.getStatus()).isEqualTo(201); var principal=mapper.readTree(response.getContentAsString());
        String token=principal.path("token").asText(); String day=LocalDate.now(ZoneId.of("Asia/Manila")).toString();
        var batch=mvc.perform(post("/api/batches").header("Authorization","Bearer "+token).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(Map.of("name","Replay fixture","initialPopulation",20,"bloodline","Test","startDate",day,"hatchDate",day,"farmEntryDate",day)))).andReturn().getResponse();
        assertThat(batch.getStatus()).isEqualTo(201);
        return new Fixture(token,principal.path("farmId").asLong(),principal.path("userId").asLong(),mapper.readTree(batch.getContentAsString()).path("id").asLong(),day);
    }
    private JsonNode sync(Fixture f,String type,UUID id,long batch,com.fasterxml.jackson.databind.node.ObjectNode payload) throws Exception {
        var response=mvc.perform(post("/api/sync/v2/operations").header("Authorization","Bearer "+f.token).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(Map.of("deviceId","receipt-matrix","operations",List.of(Map.of("operationId",id,"schemaVersion",1,"entityType",type,"batchId",batch,"occurredAt","2026-11-22T00:00:00Z","payload",payload)))))).andReturn().getResponse();
        assertThat(response.getStatus()).isEqualTo(200);return mapper.readTree(response.getContentAsString()).path("results").get(0);
    }
    private void concurrentReplay(Fixture f,String type,UUID id,com.fasterxml.jackson.databind.node.ObjectNode payload,JsonNode expected) throws Exception {
        try (var pool=java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var gate=new java.util.concurrent.CountDownLatch(1);
            java.util.concurrent.Callable<JsonNode> retry=()->{ gate.await();return sync(f,type,id,f.batch,payload.deepCopy()); };
            var left=pool.submit(retry);var right=pool.submit(retry);gate.countDown();
            assertThat(left.get(20,java.util.concurrent.TimeUnit.SECONDS)).isEqualTo(expected);
            assertThat(right.get(20,java.util.concurrent.TimeUnit.SECONDS)).isEqualTo(expected);
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM offline_command_receipt WHERE operation_id=?",Integer.class,id)).isEqualTo(1);
    }
    private com.fasterxml.jackson.databind.node.ObjectNode event(Fixture f,UUID id) {
        return mapper.createObjectNode().put("operationId",id.toString()).put("eventDate",f.day).put("eventType","BEHAVIOR_OBSERVATION").put("title","Fixture observation").put("details","original note").put("affectedCount",0);
    }
    @Test void eventOnlineAndOutboxShareImmutableReceiptAcrossArchive() throws Exception {
        var f=fixture(); UUID id=UUID.randomUUID(); var payload=event(f,id);
        var direct=mvc.perform(post("/api/batches/"+f.batch+"/events").header("Authorization","Bearer "+f.token).contentType(MediaType.APPLICATION_JSON).content(payload.toString())).andReturn().getResponse();
        assertThat(direct.getStatus()).isEqualTo(201);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM offline_command_receipt WHERE operation_id=?",Integer.class,id)).isEqualTo(1);
        var first=sync(f,"BATCH_EVENT",id,f.batch,payload); assertThat(first.path("status").asText()).isEqualTo("ALREADY_APPLIED");
        concurrentReplay(f,"BATCH_EVENT",id,payload,first);
        jdbc.update("UPDATE batch SET status='ARCHIVED' WHERE id=?",f.batch);
        var replay=sync(f,"BATCH_EVENT",id,f.batch,payload); assertThat(replay).isEqualTo(first);
        assertThat(sync(f,"BATCH_EVENT",id,f.batch,payload.deepCopy().put("details","changed")).path("status").asText()).isEqualTo("CONFLICT");
        assertThat(sync(f,"BATCH_EVENT",id,f.batch+100000,payload).path("status").asText()).isEqualTo("CONFLICT");
        assertThat(sync(fixture(),"BATCH_EVENT",id,f.batch,payload).path("status").asText()).isEqualTo("CONFLICT");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM batch_event WHERE operation_id=?",Integer.class,id)).isEqualTo(1);
        UUID unseen=UUID.randomUUID(); assertThat(sync(f,"BATCH_EVENT",unseen,f.batch,event(f,unseen)).path("status").asText()).isEqualTo("CONFLICT");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM offline_command_receipt WHERE operation_id=?",Integer.class,unseen)).isZero();
    }
    @Test void inputOnlineAndOutboxShareReceiptAndDoNotRepeatStockUsage() throws Exception {
        var f=fixture(); UUID id=UUID.randomUUID();
        var product=mvc.perform(post("/api/inventory/products").header("Authorization","Bearer "+f.token).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(Map.of("productType","FEED","brandName","Fixture feed","stockUnit","kg")))).andReturn().getResponse();
        assertThat(product.getStatus()).isEqualTo(201);long productId=mapper.readTree(product.getContentAsString()).path("id").asLong();
        var stocked=mvc.perform(post("/api/inventory/products/"+productId+"/stock-ins").header("Authorization","Bearer "+f.token).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(Map.of("quantity",10,"purchaseUnitCost",2,"operationId",UUID.randomUUID().toString())))).andReturn().getResponse();
        assertThat(stocked.getStatus()).isEqualTo(200);
        assertThat(jdbc.queryForObject("SELECT stock_on_hand FROM farm_product WHERE id=?",java.math.BigDecimal.class,productId)).isEqualByComparingTo("10");
        var payload=mapper.createObjectNode().put("operationId",id.toString()).put("batchId",f.batch).put("productType","FEED").put("brandName","Fixture feed").put("quantity",1).put("recordedAt","2026-11-22T00:00:00Z").put("notes","original input").put("farmProductId",productId).put("unit","kg");
        var direct=mvc.perform(post("/api/inputs").header("Authorization","Bearer "+f.token).contentType(MediaType.APPLICATION_JSON).content(payload.toString())).andReturn().getResponse();
        assertThat(direct.getStatus()).isEqualTo(201);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM offline_command_receipt WHERE operation_id=?",Integer.class,id)).isEqualTo(1);
        var first=sync(f,"FARM_INPUT",id,f.batch,payload); assertThat(first.path("status").asText()).isEqualTo("ALREADY_APPLIED");
        concurrentReplay(f,"FARM_INPUT",id,payload,first);
        jdbc.update("UPDATE batch SET status='ARCHIVED' WHERE id=?",f.batch);
        assertThat(sync(f,"FARM_INPUT",id,f.batch,payload)).isEqualTo(first);
        assertThat(sync(f,"FARM_INPUT",id,f.batch,payload.deepCopy().put("quantity",2)).path("status").asText()).isEqualTo("CONFLICT");
        assertThat(sync(f,"FARM_INPUT",id,f.batch+100000,payload.deepCopy().put("batchId",f.batch+100000)).path("status").asText()).isEqualTo("CONFLICT");
        assertThat(sync(fixture(),"FARM_INPUT",id,f.batch,payload).path("status").asText()).isEqualTo("CONFLICT");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM farm_input_log WHERE operation_id=?",Integer.class,id)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT stock_on_hand FROM farm_product WHERE id=?",java.math.BigDecimal.class,productId)).isEqualByComparingTo("9");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM inventory_movement WHERE operation_id=?",Integer.class,id)).isEqualTo(1);

    }

    @Test void sexCompositionReceiptSurvivesSupersedingAndArchive() throws Exception {
        var f=fixture();UUID id=UUID.randomUUID();
        var payload=mapper.createObjectNode().put("operationId",id.toString()).put("observedOn",f.day).put("maleCount",10).put("femaleCount",10).put("unclassifiedCount",0).put("notes","original sex counts");
        var direct=mvc.perform(post("/api/batches/"+f.batch+"/sex-composition").header("Authorization","Bearer "+f.token).contentType(MediaType.APPLICATION_JSON).content(payload.toString())).andReturn().getResponse();
        assertThat(direct.getStatus()).isEqualTo(200);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM offline_command_receipt WHERE operation_id=?",Integer.class,id)).isEqualTo(1);
        var first=sync(f,"SEX_COMPOSITION",id,f.batch,payload); assertThat(first.path("status").asText()).isEqualTo("ALREADY_APPLIED");
        concurrentReplay(f,"SEX_COMPOSITION",id,payload,first);
        jdbc.update("UPDATE batch_sex_composition SET status='SUPERSEDED' WHERE operation_id=?",id);
        jdbc.update("UPDATE batch SET status='ARCHIVED' WHERE id=?",f.batch);
        assertThat(sync(f,"SEX_COMPOSITION",id,f.batch,payload)).isEqualTo(first);
        assertThat(sync(f,"SEX_COMPOSITION",id,f.batch,payload.deepCopy().put("notes","changed")).path("status").asText()).isEqualTo("CONFLICT");
        assertThat(sync(f,"SEX_COMPOSITION",id,f.batch+100000,payload).path("status").asText()).isEqualTo("CONFLICT");
        assertThat(sync(fixture(),"SEX_COMPOSITION",id,f.batch,payload).path("status").asText()).isEqualTo("CONFLICT");
        assertThat(jdbc.queryForObject("SELECT status FROM batch_sex_composition WHERE operation_id=?",String.class,id)).isEqualTo("SUPERSEDED");
    }

    @Test void selectionCreationReplayDoesNotReopenOrOverwriteTheEditedSession() throws Exception {
        var f=fixture();UUID id=UUID.randomUUID();
        long review=jdbc.queryForObject("INSERT INTO batch_selection_review(farm_id,batch_id,period_start,period_end,as_of_date,status,review_status,payload_version,payload_json,generated_by,generated_at) VALUES (?,?,?,?,?,'FINALIZED','FOR_IN_PERSON_ASSESSMENT','1','{}',?,now()) RETURNING id",Long.class,f.farm,f.batch,java.sql.Date.valueOf(f.day),java.sql.Date.valueOf(f.day),java.sql.Date.valueOf(f.day),f.user);
        var payload=mapper.createObjectNode().put("operationId",id.toString()).put("selectionDate",f.day).put("evaluatedCount",10).put("acceptedCount",5).put("continueObservationCount",5).put("notAcceptedCount",0).put("otherCount",0).put("selectionReviewId",review).put("sourceDataAcknowledged",false); payload.putArray("criterionCodes");
        var direct=mvc.perform(post("/api/batches/"+f.batch+"/selection-sessions").header("Authorization","Bearer "+f.token).contentType(MediaType.APPLICATION_JSON).content(payload.toString())).andReturn().getResponse();
        assertThat(direct.getStatus()).isEqualTo(200);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM offline_command_receipt WHERE operation_id=?",Integer.class,id)).isEqualTo(1);
        var first=sync(f,"SELECTION_SESSION",id,f.batch,payload); assertThat(first.path("status").asText()).isEqualTo("ALREADY_APPLIED");
        concurrentReplay(f,"SELECTION_SESSION",id,payload,first);
        jdbc.update("UPDATE batch_selection_session SET accepted_count=6,continue_observation_count=4,status='FINALIZED',version=version+1 WHERE operation_id=?",id);
        jdbc.update("UPDATE batch SET status='ARCHIVED' WHERE id=?",f.batch);
        assertThat(sync(f,"SELECTION_SESSION",id,f.batch,payload)).isEqualTo(first);
        assertThat(sync(f,"SELECTION_SESSION",id,f.batch,payload.deepCopy().put("acceptedCount",4)).path("status").asText()).isEqualTo("CONFLICT");
        assertThat(sync(f,"SELECTION_SESSION",id,f.batch+100000,payload).path("status").asText()).isEqualTo("CONFLICT");
        assertThat(sync(fixture(),"SELECTION_SESSION",id,f.batch,payload).path("status").asText()).isEqualTo("CONFLICT");
        assertThat(jdbc.queryForObject("SELECT accepted_count FROM batch_selection_session WHERE operation_id=?",Integer.class,id)).isEqualTo(6);
        assertThat(jdbc.queryForObject("SELECT status FROM batch_selection_session WHERE operation_id=?",String.class,id)).isEqualTo("FINALIZED");
    }

    @Test void taskReceiptBindsNotesAndTimeAndCannotReplayAnOldStatusOverANewOne() throws Exception {
        var f=fixture();UUID id=UUID.randomUUID();
        var created=mvc.perform(post("/api/tasks").header("Authorization","Bearer "+f.token).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(Map.of("title","Replay task","batchId",f.batch)))).andReturn().getResponse();
        assertThat(created.getStatus()).isEqualTo(201);long task=mapper.readTree(created.getContentAsString()).path("id").asLong();
        var body=mapper.createObjectNode().put("operationId",id.toString()).put("status","IN_PROGRESS").put("note","original task note").put("occurredAt","2026-11-22T00:00:00Z");
        var direct=mvc.perform(post("/api/tasks/"+task+"/status").header("Authorization","Bearer "+f.token).contentType(MediaType.APPLICATION_JSON).content(body.toString())).andReturn().getResponse();
        assertThat(direct.getStatus()).isEqualTo(200);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM offline_command_receipt WHERE operation_id=?",Integer.class,id)).isEqualTo(1);
        var payload=body.deepCopy().put("taskId",task);var first=sync(f,"TASK_STATUS",id,f.batch,payload);assertThat(first.path("status").asText()).isEqualTo("ALREADY_APPLIED");
        concurrentReplay(f,"TASK_STATUS",id,payload,first);
        jdbc.update("UPDATE handler_task SET status='BLOCKED',completion_note='later status' WHERE id=?",task);
        jdbc.update("UPDATE batch SET status='ARCHIVED' WHERE id=?",f.batch);
        assertThat(sync(f,"TASK_STATUS",id,f.batch,payload)).isEqualTo(first);
        assertThat(sync(f,"TASK_STATUS",id,f.batch,payload.deepCopy().put("note","changed")).path("status").asText()).isEqualTo("CONFLICT");
        assertThat(sync(f,"TASK_STATUS",id,f.batch,payload.deepCopy().put("occurredAt","2099-01-01T00:00:00Z")).path("status").asText()).isEqualTo("CONFLICT");
        assertThat(sync(f,"TASK_STATUS",id,f.batch,payload.deepCopy().put("taskId",task+100000)).path("status").asText()).isIn("CONFLICT","REJECTED");
        assertThat(sync(fixture(),"TASK_STATUS",id,f.batch,payload).path("status").asText()).isIn("CONFLICT","REJECTED");
        assertThat(jdbc.queryForObject("SELECT status FROM handler_task WHERE id=?",String.class,task)).isEqualTo("BLOCKED");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM handler_task_status_history WHERE operation_id=?",Integer.class,id)).isEqualTo(1);
    }

    @Test void vaccinationReceiptHasOneCompletionInputStockUseAndTaskCompletion() throws Exception {
        var f=fixture();UUID id=UUID.randomUUID();
        var product=mvc.perform(post("/api/inventory/products").header("Authorization","Bearer "+f.token).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(Map.of("productType","VACCINE","brandName","Matrix vaccine","stockUnit","vial")))).andReturn().getResponse();
        assertThat(product.getStatus()).isEqualTo(201);long productId=mapper.readTree(product.getContentAsString()).path("id").asLong();
        assertThat(mvc.perform(post("/api/inventory/products/"+productId+"/stock-ins").header("Authorization","Bearer "+f.token).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(Map.of("quantity",10,"purchaseUnitCost",5,"operationId",UUID.randomUUID().toString())))).andReturn().getResponse().getStatus()).isEqualTo(200);
        var program=mvc.perform(post("/api/vaccination-programs").header("Authorization","Bearer "+f.token).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(Map.of("name","Matrix program","items",List.of(Map.of("vaccineName","Matrix vaccine","farmProductId",productId,"ageOffsetValue",0,"ageOffsetUnit","DAY")))))).andReturn().getResponse();
        assertThat(program.getStatus()).isEqualTo(200);long programId=mapper.readTree(program.getContentAsString()).path("id").asLong();
        var assigned=mvc.perform(post("/api/batches/"+f.batch+"/vaccination-plan").header("Authorization","Bearer "+f.token).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(Map.of("programId",programId)))).andReturn().getResponse();
        assertThat(assigned.getStatus()).isEqualTo(200);var plan=mapper.readTree(assigned.getContentAsString()).get(0);long planId=plan.path("id").asLong();long taskId=plan.path("taskId").asLong();
        var body=mapper.createObjectNode().put("operationId",id.toString()).put("quantity",1).put("unit","vial").put("recordedAt","2026-11-22T00:00:00Z").put("notes","original vaccine note");
        assertThat(sync(f,"VACCINATION_PLAN",id,f.batch+100000,body.deepCopy().put("planId",planId)).path("status").asText()).isEqualTo("CONFLICT");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM offline_command_receipt WHERE operation_id=?",Integer.class,id)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM farm_input_log WHERE operation_id=?",Integer.class,id)).isZero();
        assertThat(jdbc.queryForObject("SELECT stock_on_hand FROM farm_product WHERE id=?",java.math.BigDecimal.class,productId)).isEqualByComparingTo("10");
        var direct=mvc.perform(post("/api/vaccination-plan-items/"+planId+"/record").header("Authorization","Bearer "+f.token).contentType(MediaType.APPLICATION_JSON).content(body.toString())).andReturn().getResponse();
        assertThat(direct.getStatus()).isEqualTo(200);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM offline_command_receipt WHERE operation_id=?",Integer.class,id)).isEqualTo(1);
        var payload=body.deepCopy().put("planId",planId);var first=sync(f,"VACCINATION_PLAN",id,f.batch,payload);assertThat(first.path("status").asText()).isEqualTo("ALREADY_APPLIED");
        concurrentReplay(f,"VACCINATION_PLAN",id,payload,first);
        jdbc.update("UPDATE batch SET status='ARCHIVED' WHERE id=?",f.batch);
        assertThat(sync(f,"VACCINATION_PLAN",id,f.batch,payload)).isEqualTo(first);
        assertThat(sync(f,"VACCINATION_PLAN",id,f.batch,payload.deepCopy().put("quantity",2)).path("status").asText()).isEqualTo("CONFLICT");
        assertThat(sync(f,"VACCINATION_PLAN",id,f.batch,payload.deepCopy().put("notes","changed")).path("status").asText()).isEqualTo("CONFLICT");
        assertThat(sync(f,"VACCINATION_PLAN",id,f.batch,payload.deepCopy().put("planId",planId+100000)).path("status").asText()).isEqualTo("CONFLICT");
        assertThat(sync(fixture(),"VACCINATION_PLAN",id,f.batch,payload).path("status").asText()).isEqualTo("CONFLICT");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM farm_input_log WHERE operation_id=?",Integer.class,id)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM inventory_movement WHERE operation_id=?",Integer.class,id)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT stock_on_hand FROM farm_product WHERE id=?",java.math.BigDecimal.class,productId)).isEqualByComparingTo("9");
        assertThat(jdbc.queryForObject("SELECT status FROM handler_task WHERE id=?",String.class,taskId)).isEqualTo("COMPLETED");
    }

    @Test void dailyMeasurementGetsSharedReceiptAndLegacyEvidenceDoesNotWriteAgain() throws Exception {
        var f=fixture();UUID id=UUID.randomUUID();
        var payload=mapper.createObjectNode().put("operationId",id.toString()).put("ownerUserId",f.user).put("ownerFarmId",f.farm).put("recordDate",f.day).put("temperatureC",30).put("behaviorNotes","original measurement");
        var first=sync(f,"DAILY_RECORD",id,f.batch,payload);assertThat(first.path("status").asText()).isEqualTo("APPLIED");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM offline_command_receipt WHERE operation_id=?",Integer.class,id)).isEqualTo(1);
        var replay=sync(f,"DAILY_RECORD",id,f.batch,payload);assertThat(replay.path("status").asText()).isEqualTo("ALREADY_APPLIED");
        concurrentReplay(f,"DAILY_RECORD",id,payload,replay);
        assertThat(replay.path("serverId")).isEqualTo(first.path("serverId"));assertThat(replay.path("serverTime")).isEqualTo(first.path("serverTime"));
        jdbc.update("UPDATE batch SET status='ARCHIVED' WHERE id=?",f.batch);
        assertThat(sync(f,"DAILY_RECORD",id,f.batch,payload)).isEqualTo(replay);
        assertThat(sync(f,"DAILY_RECORD",id,f.batch,payload.deepCopy().put("temperatureC",31)).path("status").asText()).isEqualTo("CONFLICT");
        assertThat(sync(f,"DAILY_RECORD",id,f.batch+100000,payload).path("status").asText()).isEqualTo("CONFLICT");
        assertThat(sync(fixture(),"DAILY_RECORD",id,f.batch,payload).path("status").asText()).isEqualTo("CONFLICT");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM daily_record WHERE batch_id=?",Integer.class,f.batch)).isEqualTo(1);
        // Rehearse adoption of the full immutable V27 receipt without a new measurement.
        jdbc.update("DELETE FROM offline_command_receipt WHERE operation_id=?",id);
        var adopted=sync(f,"DAILY_RECORD",id,f.batch,payload);assertThat(adopted.path("status").asText()).isEqualTo("ALREADY_APPLIED");
        assertThat(adopted.path("serverId")).isEqualTo(first.path("serverId"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM offline_command_receipt WHERE operation_id=?",Integer.class,id)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM daily_record WHERE batch_id=?",Integer.class,f.batch)).isEqualTo(1);
    }

    @Test void legacyFutureClockCannotOverwriteAnExistingMeasurement() throws Exception {
        var f=fixture();
        var created=mvc.perform(post("/api/batches/"+f.batch+"/records").header("Authorization","Bearer "+f.token).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(Map.of("recordDate",f.day,"temperatureC",30,"behaviorNotes","authoritative")))).andReturn().getResponse();
        assertThat(created.getStatus()).isEqualTo(201);
        var legacy=mvc.perform(post("/api/sync/batch").header("Authorization","Bearer "+f.token).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(Map.of("records",List.of(Map.of("clientId","legacy-fixture","batchId",f.batch,"recordDate",f.day,"temperatureC",40,"behaviorNotes","stale edit with future clock","updatedAt","2099-01-01T00:00:00Z")))))).andReturn().getResponse();
        assertThat(legacy.getStatus()).isEqualTo(200);
        assertThat(mapper.readTree(legacy.getContentAsString()).path("conflicts").asInt()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT temperaturec FROM daily_record WHERE batch_id=?",Double.class,f.batch)).isEqualTo(30);
    }

    @Test void sharedDailyReceiptsDoNotRequireAnUndeployedLegacyV27Table() throws Exception {
        var f=fixture();UUID id=UUID.randomUUID();var payload=mapper.createObjectNode().put("operationId",id.toString()).put("ownerUserId",f.user).put("ownerFarmId",f.farm).put("recordDate",f.day).put("temperatureC",30);
        jdbc.execute("ALTER TABLE offline_daily_record_receipt RENAME TO held_legacy_daily_receipt");
        try {
            var applied=sync(f,"DAILY_RECORD",id,f.batch,payload);assertThat(applied.path("status").asText()).isEqualTo("APPLIED");
            var replay=sync(f,"DAILY_RECORD",id,f.batch,payload);assertThat(replay.path("status").asText()).isEqualTo("ALREADY_APPLIED");
            assertThat(replay.path("serverTime")).isEqualTo(applied.path("serverTime"));
            assertThat(jdbc.queryForObject("SELECT count(*) FROM daily_record WHERE batch_id=?",Integer.class,f.batch)).isEqualTo(1);
        } finally { jdbc.execute("ALTER TABLE held_legacy_daily_receipt RENAME TO offline_daily_record_receipt"); }
    }

    @Test void legacyNewClassificationCannotOverwriteARecordCommittedBeforeItsWrite() throws Exception {
        var f=fixture();
        var records=context.getBean(com.poultryprophet.record.DailyRecordService.class);
        var resolver=new com.poultryprophet.sync.ConflictResolver() {
            @Override public com.poultryprophet.sync.dto.ConflictType classify(com.poultryprophet.record.DailyRecord existing,com.poultryprophet.sync.dto.SyncItemRequest incoming) {
                assertThat(existing).isNull();
                // Deterministically interleave a second request between legacy read and write.
                records.record(f.batch,f.farm,new com.poultryprophet.record.dto.CreateRecordRequest(LocalDate.parse(f.day),30.0,null,null,null,"accepted while legacy request waited"),f.user);
                return super.classify(existing,incoming);
            }
        };
        var legacy=new com.poultryprophet.sync.SyncService(records,context.getBean(com.poultryprophet.record.DailyRecordRepository.class),resolver,
                context.getBean(com.poultryprophet.sync.SyncAttemptRepository.class),context.getBean(com.poultryprophet.batch.BatchService.class),context.getBean(com.poultryprophet.common.DateValidationService.class));
        var response=legacy.sync(new com.poultryprophet.sync.dto.SyncBatchRequest(List.of(new com.poultryprophet.sync.dto.SyncItemRequest("interleaved",f.batch,LocalDate.parse(f.day),40.0,null,null,null,"stale legacy",Instant.parse("2099-01-01T00:00:00Z")))),f.farm,f.user);
        assertThat(jdbc.queryForObject("SELECT temperaturec FROM daily_record WHERE batch_id=?",Double.class,f.batch)).isEqualTo(30);
        assertThat(response.conflicts()).isEqualTo(1);assertThat(response.synced()).isZero();
    }

    @Test void saleReplayCannotRepeatIncomeOrPopulationReduction() throws Exception {
        var f=fixture();UUID id=UUID.randomUUID();var payload=event(f,id).put("eventType","SALE").put("affectedCount",1);
        payload.putObject("saleDetails").put("amount",250).put("currency","PHP").put("buyer","Synthetic buyer");
        var applied=sync(f,"BATCH_EVENT",id,f.batch,payload);assertThat(applied.path("status").asText()).isEqualTo("APPLIED");
        var replay=sync(f,"BATCH_EVENT",id,f.batch,payload);assertThat(replay.path("status").asText()).isEqualTo("ALREADY_APPLIED");
        concurrentReplay(f,"BATCH_EVENT",id,payload,replay);
        assertThat(jdbc.queryForObject("SELECT current_population FROM batch WHERE id=?",Integer.class,f.batch)).isEqualTo(19);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM farm_financial_transaction WHERE source_operation_id=?",Integer.class,id.toString())).isEqualTo(1);
        assertThat(sync(f,"BATCH_EVENT",id,f.batch,payload.deepCopy().put("affectedCount",2)).path("status").asText()).isEqualTo("CONFLICT");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM batch_event WHERE operation_id=?",Integer.class,id)).isEqualTo(1);
    }

}
