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
    private Fixture fixture() throws Exception { return fixture(20); }
    private Fixture fixture(int population) throws Exception {
        var response=mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(Map.of("email",UUID.randomUUID()+"@example.test","password","test-password-123","fullName","Replay Manager","farmName","Synthetic Replay Farm")))).andReturn().getResponse();
        assertThat(response.getStatus()).isEqualTo(201); var principal=mapper.readTree(response.getContentAsString());
        String token=principal.path("token").asText(); String day=LocalDate.now(ZoneId.of("Asia/Manila")).toString();
        var batch=mvc.perform(post("/api/batches").header("Authorization","Bearer "+token).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(Map.of("name","Replay fixture","initialPopulation",population,"bloodline","Test","startDate",day,"hatchDate",day,"farmEntryDate",day)))).andReturn().getResponse();
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
    @Test void fiftyDeathsUpdateEveryLivePopulationViewForBothRolesExactlyOnce() throws Exception {
        var manager = fixture(200);
        String email = "handler-" + UUID.randomUUID() + "@example.test";
        var registered = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("email", email, "password", "test-password-123", "fullName", "Local Handler"))))
                .andReturn().getResponse();
        assertThat(registered.getStatus()).isEqualTo(201);
        long handlerId = mapper.readTree(registered.getContentAsString()).path("userId").asLong();
        jdbc.update("UPDATE app_user SET role='HANDLER', farm_id=? WHERE id=?", manager.farm, handlerId);
        var login = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("email", email, "password", "test-password-123"))))
                .andReturn().getResponse();
        assertThat(login.getStatus()).isEqualTo(200);
        var handler = new Fixture(mapper.readTree(login.getContentAsString()).path("token").asText(),
                manager.farm, handlerId, manager.batch, manager.day);
        UUID id = UUID.randomUUID();
        var payload = mapper.createObjectNode().put("operationId", id.toString()).put("eventDate", manager.day)
                .put("eventType", "HEALTH_DEATH").put("title", "Health-related death").put("affectedCount", 50)
                .put("details", "Signs noticed: Weakness").put("deathObservation", "SIGNS").put("deathSigns", "Weakness");
        assertThat(sync(handler, "BATCH_EVENT", id, manager.batch, payload).path("status").asText()).isEqualTo("APPLIED");
        var replay = sync(handler, "BATCH_EVENT", id, manager.batch, payload);
        assertThat(replay.path("status").asText()).isEqualTo("ALREADY_APPLIED");
        concurrentReplay(handler, "BATCH_EVENT", id, payload, replay);

        for (String token : List.of(manager.token, handler.token)) {
            for (String suffix : List.of("", "/overview", "/selection-review/preview")) {
                var response = mvc.perform(get("/api/batches/" + manager.batch + suffix)
                        .header("Authorization", "Bearer " + token)).andReturn().getResponse();
                assertThat(response.getStatus()).as(suffix).isEqualTo(200);
                var body = mapper.readTree(response.getContentAsString());
                var population = suffix.isEmpty() ? body : suffix.equals("/overview") ? body.path("batch") : body.path("population");
                assertThat(population.path("initialPopulation").asInt()).isEqualTo(200);
                assertThat(population.path("currentPopulation").asInt()).isEqualTo(150);
                if (suffix.equals("/selection-review/preview")) assertThat(population.path("totalDeaths").asInt()).isEqualTo(50);
            }
            var response = mvc.perform(get("/api/batches/dashboard").header("Authorization", "Bearer " + token)).andReturn().getResponse();
            assertThat(response.getStatus()).isEqualTo(200);
            var cards = mapper.readTree(response.getContentAsString());
            boolean found = false;
            for (var card : cards) if (card.path("batch").path("id").asLong() == manager.batch) {
                found = true;
                assertThat(card.path("batch").path("currentPopulation").asInt()).isEqualTo(150);
                assertThat(card.path("summary").path("totalDeaths").asInt()).isEqualTo(50);
            }
            assertThat(found).isTrue();
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM batch_event WHERE operation_id=?", Integer.class, id)).isEqualTo(1);
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
        assertThat(jdbc.queryForObject("SELECT calculated_cost FROM farm_input_log WHERE operation_id=?",java.math.BigDecimal.class,id)).isEqualByComparingTo("5.00");
        assertThat(jdbc.queryForObject("SELECT stock_on_hand FROM farm_product WHERE id=?",java.math.BigDecimal.class,productId)).isEqualByComparingTo("9");
        assertThat(jdbc.queryForObject("SELECT status FROM handler_task WHERE id=?",String.class,taskId)).isEqualTo("COMPLETED");
    }

    @Test void unscheduledVaccineInputDoesNotCompleteAPlannedDose() throws Exception {
        var f=fixture(); UUID id=UUID.randomUUID();
        var product=mvc.perform(post("/api/inventory/products").header("Authorization","Bearer "+f.token).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(Map.of("productType","VACCINE","brandName","Vax","stockUnit","vial","openingQuantity",10,"openingUnitCost",5)))).andReturn().getResponse();
        long productId=mapper.readTree(product.getContentAsString()).path("id").asLong();
        var program=mvc.perform(post("/api/vaccination-programs").header("Authorization","Bearer "+f.token).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(Map.of("name","Plan","items",List.of(Map.of("vaccineName","Vax","farmProductId",productId,"ageOffsetValue",0,"ageOffsetUnit","DAY")))))).andReturn().getResponse();
        long programId=mapper.readTree(program.getContentAsString()).path("id").asLong();
        var assigned=mvc.perform(post("/api/batches/"+f.batch+"/vaccination-plan").header("Authorization","Bearer "+f.token).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(Map.of("programId",programId)))).andReturn().getResponse();
        long planId=mapper.readTree(assigned.getContentAsString()).get(0).path("id").asLong();
        var payload=mapper.createObjectNode().put("batchId",f.batch).put("productType","VACCINE").put("brandName","Vax").put("farmProductId",productId).put("quantity",1).put("unit","vial").put("purpose","Vaccination").put("operationId",id.toString());
        assertThat(sync(f,"FARM_INPUT",id,f.batch,payload).path("status").asText()).isEqualTo("APPLIED");
        assertThat(jdbc.queryForObject("SELECT status FROM batch_vaccination_plan_item WHERE id=?",String.class,planId)).isEqualTo("SCHEDULED");
    }

    @Test void vaccinationSkipCannotModifyAnArchivedBatchOrItsTask() throws Exception {
        var f=fixture();
        var program=mvc.perform(post("/api/vaccination-programs").header("Authorization","Bearer "+f.token)
                .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(Map.of("name","Legacy schedule",
                        "items",List.of(Map.of("vaccineName","Vax","ageOffsetValue",0,"ageOffsetUnit","DAY")))))).andReturn().getResponse();
        assertThat(program.getStatus()).isEqualTo(200);
        var assigned=mvc.perform(post("/api/batches/"+f.batch+"/vaccination-plan").header("Authorization","Bearer "+f.token)
                .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(Map.of("programId",mapper.readTree(program.getContentAsString()).path("id").asLong())))).andReturn().getResponse();
        assertThat(assigned.getStatus()).isEqualTo(200);
        var item=mapper.readTree(assigned.getContentAsString()).get(0); long planId=item.path("id").asLong(), taskId=item.path("taskId").asLong();
        jdbc.update("UPDATE batch SET status='ARCHIVED' WHERE id=?",f.batch);
        var skipped=mvc.perform(post("/api/vaccination-plan-items/"+planId+"/skip").header("Authorization","Bearer "+f.token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Not given\"}")).andReturn().getResponse();
        assertThat(skipped.getStatus()).isEqualTo(409);
        assertThat(jdbc.queryForObject("SELECT status FROM batch_vaccination_plan_item WHERE id=?",String.class,planId)).isEqualTo("SCHEDULED");
        assertThat(jdbc.queryForObject("SELECT status FROM handler_task WHERE id=?",String.class,taskId)).isEqualTo("TODO");
    }

    @Test void productCreateKeySerializesConcurrentRequestsAndBindsOwnershipAndPayload() throws Exception {
        var f=fixture(); var other=fixture(); UUID key=UUID.randomUUID();
        var body=mapper.createObjectNode().put("productType","VACCINE").put("brandName","Keyed product")
                .put("stockUnit","vial").put("openingQuantity",10).put("openingUnitCost",10);
        java.util.concurrent.Callable<org.springframework.mock.web.MockHttpServletResponse> create = () -> mvc.perform(
                post("/api/inventory/products").header("Authorization","Bearer "+f.token).header("Idempotency-Key",key)
                        .contentType(MediaType.APPLICATION_JSON).content(body.toString())).andReturn().getResponse();
        long productId;
        try (var pool=java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var gate=new java.util.concurrent.CountDownLatch(1);
            var a=pool.submit(() -> {gate.await();return create.call();});
            var b=pool.submit(() -> {gate.await();return create.call();}); gate.countDown();
            var first=a.get(20,java.util.concurrent.TimeUnit.SECONDS); var second=b.get(20,java.util.concurrent.TimeUnit.SECONDS);
            assertThat(first.getStatus()).isEqualTo(201); assertThat(second.getStatus()).isEqualTo(201);
            productId=mapper.readTree(first.getContentAsString()).path("id").asLong();
            assertThat(mapper.readTree(second.getContentAsString()).path("id").asLong()).isEqualTo(productId);
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM farm_product WHERE farm_id=? AND brand_name='Keyed product'",Integer.class,f.farm)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT stock_on_hand FROM farm_product WHERE id=?",java.math.BigDecimal.class,productId)).isEqualByComparingTo("10");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM inventory_movement WHERE farm_product_id=? AND movement_type='OPENING_BALANCE'",Integer.class,productId)).isEqualTo(1);
        var changed=mvc.perform(post("/api/inventory/products").header("Authorization","Bearer "+f.token).header("Idempotency-Key",key)
                .contentType(MediaType.APPLICATION_JSON).content(body.deepCopy().put("openingQuantity",11).toString())).andReturn().getResponse();
        assertThat(changed.getStatus()).isEqualTo(409);
        var foreign=mvc.perform(post("/api/inventory/products").header("Authorization","Bearer "+other.token).header("Idempotency-Key",key)
                .contentType(MediaType.APPLICATION_JSON).content(body.toString())).andReturn().getResponse();
        assertThat(foreign.getStatus()).isEqualTo(409);
        jdbc.update("UPDATE app_user SET farm_id=? WHERE id=?",f.farm,other.user);
        var actor=mvc.perform(post("/api/inventory/products").header("Authorization","Bearer "+other.token).header("Idempotency-Key",key)
                .contentType(MediaType.APPLICATION_JSON).content(body.toString())).andReturn().getResponse();
        assertThat(actor.getStatus()).isEqualTo(409);
    }

    @Test void productCreateRollsBackReceiptOnInvalidOpeningAndAllowsDistinctIntent() throws Exception {
        var f=fixture();UUID key=UUID.randomUUID();
        var body=mapper.createObjectNode().put("productType","VACCINE").put("brandName","Product after validation")
                .put("stockUnit","vial").put("openingQuantity",1.5).put("openingUnitCost",10).put("allowFractionalQuantity",false);
        var invalid=mvc.perform(post("/api/inventory/products").header("Authorization","Bearer "+f.token).header("Idempotency-Key",key)
                .contentType(MediaType.APPLICATION_JSON).content(body.toString())).andReturn().getResponse();
        assertThat(invalid.getStatus()).isEqualTo(400);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM offline_command_receipt WHERE operation_id=?",Integer.class,key)).isZero();
        body.put("openingQuantity",1);
        var valid=mvc.perform(post("/api/inventory/products").header("Authorization","Bearer "+f.token).header("Idempotency-Key",key)
                .contentType(MediaType.APPLICATION_JSON).content(body.toString())).andReturn().getResponse();
        assertThat(valid.getStatus()).isEqualTo(201);
        var legacy=mvc.perform(post("/api/inventory/products").header("Authorization","Bearer "+f.token)
                .contentType(MediaType.APPLICATION_JSON).content(body.toString())).andReturn().getResponse();
        assertThat(legacy.getStatus()).isEqualTo(201);
        assertThat(mapper.readTree(valid.getContentAsString()).path("id")).isNotEqualTo(mapper.readTree(legacy.getContentAsString()).path("id"));
    }

    @Test void manualExpenseCreateKeySerializesConcurrentRequestsAndBindsPayloadAndFarm() throws Exception {
        var f = fixture(); var other = fixture(); UUID key = UUID.randomUUID();
        var body = mapper.createObjectNode().put("batchId", f.batch).put("transactionDate", f.day)
                .put("type", "EXPENSE").put("category", "Transport").put("amount", 100);
        java.util.concurrent.Callable<org.springframework.mock.web.MockHttpServletResponse> create = () -> mvc.perform(
                post("/api/financial-transactions").header("Authorization", "Bearer " + f.token)
                    .header("Idempotency-Key", key).contentType(MediaType.APPLICATION_JSON).content(body.toString()))
                .andReturn().getResponse();
        try (var pool = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var gate = new java.util.concurrent.CountDownLatch(1);
            var a = pool.submit(() -> { gate.await(); return create.call(); });
            var b = pool.submit(() -> { gate.await(); return create.call(); });
            gate.countDown();
            var first = a.get(20, java.util.concurrent.TimeUnit.SECONDS);
            var second = b.get(20, java.util.concurrent.TimeUnit.SECONDS);
            assertThat(first.getStatus()).isEqualTo(201);
            assertThat(second.getStatus()).isEqualTo(201);
            assertThat(mapper.readTree(first.getContentAsString()).path("id"))
                    .isEqualTo(mapper.readTree(second.getContentAsString()).path("id"));
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM farm_financial_transaction WHERE batch_id=?", Integer.class, f.batch)).isEqualTo(1);
        var changed = mvc.perform(post("/api/financial-transactions").header("Authorization", "Bearer " + f.token)
                .header("Idempotency-Key", key).contentType(MediaType.APPLICATION_JSON).content(body.deepCopy().put("amount", 101).toString())).andReturn().getResponse();
        assertThat(changed.getStatus()).isEqualTo(409);
        var foreign = mvc.perform(post("/api/financial-transactions").header("Authorization", "Bearer " + other.token)
                .header("Idempotency-Key", key).contentType(MediaType.APPLICATION_JSON).content(body.toString())).andReturn().getResponse();
        assertThat(foreign.getStatus()).isEqualTo(409);
        jdbc.update("UPDATE app_user SET farm_id=? WHERE id=?", f.farm, other.user);
        var otherActor = mvc.perform(post("/api/financial-transactions").header("Authorization", "Bearer " + other.token)
                .header("Idempotency-Key", key).contentType(MediaType.APPLICATION_JSON).content(body.toString())).andReturn().getResponse();
        assertThat(otherActor.getStatus()).isEqualTo(409);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM farm_financial_transaction WHERE batch_id=?", Integer.class, f.batch)).isEqualTo(1);
    }

    @Test void rejectedExpenseRollsBackReceiptAndIndependentIdenticalExpensesRemainDistinct() throws Exception {
        var f=fixture(); UUID key=UUID.randomUUID();
        var body=mapper.createObjectNode().put("batchId",f.batch).put("transactionDate",f.day)
                .put("type","EXPENSE").put("category","Transport").put("amount",100);
        var rejected=mvc.perform(post("/api/financial-transactions").header("Authorization","Bearer "+f.token)
                .header("Idempotency-Key",key).contentType(MediaType.APPLICATION_JSON)
                .content(body.deepCopy().put("batchId",f.batch+100000).toString())).andReturn().getResponse();
        assertThat(rejected.getStatus()).isEqualTo(404);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM offline_command_receipt WHERE operation_id=?",Integer.class,key)).isZero();
        var valid=mvc.perform(post("/api/financial-transactions").header("Authorization","Bearer "+f.token)
                .header("Idempotency-Key",key).contentType(MediaType.APPLICATION_JSON).content(body.toString())).andReturn().getResponse();
        assertThat(valid.getStatus()).isEqualTo(201);
        var distinct=mvc.perform(post("/api/financial-transactions").header("Authorization","Bearer "+f.token)
                .header("Idempotency-Key",UUID.randomUUID()).contentType(MediaType.APPLICATION_JSON).content(body.toString())).andReturn().getResponse();
        assertThat(distinct.getStatus()).isEqualTo(201);
        var legacy=mvc.perform(post("/api/financial-transactions").header("Authorization","Bearer "+f.token)
                .contentType(MediaType.APPLICATION_JSON).content(body.toString())).andReturn().getResponse();
        assertThat(legacy.getStatus()).isEqualTo(201);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM farm_financial_transaction WHERE batch_id=?",Integer.class,f.batch)).isEqualTo(3);
    }

    @Test void batchExpenseCorrectionIsAtomicIdempotentAndScoped() throws Exception {
        var f=fixture(); var other=fixture(); UUID operation=UUID.randomUUID();
        var create=mapper.createObjectNode().put("batchId",f.batch).put("transactionDate",f.day).put("type","EXPENSE").put("category","Transport").put("amount",100);
        var created=mvc.perform(post("/api/financial-transactions").header("Authorization","Bearer "+f.token).contentType(MediaType.APPLICATION_JSON).content(create.toString())).andReturn().getResponse();
        assertThat(created.getStatus()).isEqualTo(201);
        var original=mapper.readTree(created.getContentAsString()); long originalId=original.path("id").asLong();
        var correction=mapper.createObjectNode().put("transactionDate",f.day).put("category","Transport").put("amount",120).put("reason","Wrong amount").put("expectedUpdatedAt",original.path("updatedAt").asText()).put("operationId",operation.toString());
        var path="/api/financial-transactions/"+originalId+"/corrections";
        assertThat(mvc.perform(post(path).header("Authorization","Bearer "+other.token).contentType(MediaType.APPLICATION_JSON).content(correction.toString())).andReturn().getResponse().getStatus()).isEqualTo(404);
        var first=mvc.perform(post(path).header("Authorization","Bearer "+f.token).contentType(MediaType.APPLICATION_JSON).content(correction.toString())).andReturn().getResponse();
        assertThat(first.getStatus()).isEqualTo(200); var replacement=mapper.readTree(first.getContentAsString()); assertThat(replacement.path("correctionOfId").asLong()).isEqualTo(originalId);
        var replay=mvc.perform(post(path).header("Authorization","Bearer "+f.token).contentType(MediaType.APPLICATION_JSON).content(correction.toString())).andReturn().getResponse();
        assertThat(replay.getStatus()).isEqualTo(200); assertThat(mapper.readTree(replay.getContentAsString()).path("id")).isEqualTo(replacement.path("id"));
        assertThat(mvc.perform(post(path).header("Authorization","Bearer "+f.token).contentType(MediaType.APPLICATION_JSON).content(correction.deepCopy().put("amount",130).toString())).andReturn().getResponse().getStatus()).isEqualTo(409);
        assertThat(mvc.perform(post(path).header("Authorization","Bearer "+f.token).contentType(MediaType.APPLICATION_JSON).content(correction.deepCopy().put("expectedUpdatedAt","2026-01-01T00:00:00Z").toString())).andReturn().getResponse().getStatus()).isEqualTo(409);
        jdbc.update("UPDATE app_user SET farm_id=? WHERE id=?",f.farm,other.user);
        assertThat(mvc.perform(post(path).header("Authorization","Bearer "+other.token).contentType(MediaType.APPLICATION_JSON).content(correction.toString())).andReturn().getResponse().getStatus()).isEqualTo(409);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM farm_financial_transaction WHERE correction_of_id=?",Integer.class,originalId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT sum(amount) FROM farm_financial_transaction WHERE batch_id=? AND status='POSTED'",java.math.BigDecimal.class,f.batch)).isEqualByComparingTo("120.00");
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

    private org.springframework.mock.web.MockHttpServletResponse resolveManager(Fixture f, String kind, String target, UUID id, JsonNode payload) throws Exception {
        return mvc.perform(post("/api/manager-operations/resolve").header("Authorization","Bearer "+f.token)
                .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(Map.of(
                        "kind",kind,"targetId",target,"operationId",id.toString(),"payload",payload)))).andReturn().getResponse();
    }

    @Test void cancelledExpenseFencesLateWritesAndBindsOwnerAndPayload() throws Exception {
        var f=fixture(); var other=fixture(); UUID key=UUID.randomUUID();
        var body=mapper.createObjectNode().put("batchId",f.batch).put("transactionDate",f.day).put("type","EXPENSE").put("category","Transport").put("amount",100);
        var cancelled=resolveManager(f,"FINANCE_CREATE",Long.toString(f.batch),key,body);
        assertThat(cancelled.getStatus()).isEqualTo(200);
        assertThat(mapper.readTree(cancelled.getContentAsString()).path("status").asText()).isEqualTo("CANCELLED");
        assertThat(mapper.readTree(resolveManager(f,"FINANCE_CREATE",Long.toString(f.batch),key,body).getContentAsString()).path("status").asText()).isEqualTo("CANCELLED");
        assertThat(resolveManager(f,"FINANCE_CREATE",Long.toString(f.batch),key,body.deepCopy().put("amount",101)).getStatus()).isEqualTo(409);
        assertThat(resolveManager(other,"FINANCE_CREATE",Long.toString(f.batch),key,body).getStatus()).isEqualTo(409);
        var late=mvc.perform(post("/api/financial-transactions").header("Authorization","Bearer "+f.token).header("Idempotency-Key",key)
                .contentType(MediaType.APPLICATION_JSON).content(body.toString())).andReturn().getResponse();
        assertThat(late.getStatus()).isEqualTo(409);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM farm_financial_transaction WHERE batch_id=?",Integer.class,f.batch)).isZero();
        UUID applied=UUID.randomUUID();
        var created=mvc.perform(post("/api/financial-transactions").header("Authorization","Bearer "+f.token).header("Idempotency-Key",applied)
                .contentType(MediaType.APPLICATION_JSON).content(body.toString())).andReturn().getResponse();
        assertThat(created.getStatus()).isEqualTo(201);
        var observed=resolveManager(f,"FINANCE_CREATE",Long.toString(f.batch),applied,body);
        assertThat(mapper.readTree(observed.getContentAsString()).path("status").asText()).isEqualTo("APPLIED");
    }

    @Test void stockInCancelRacesCommitWithoutRepeatingStockOrCost() throws Exception {
        var f=fixture(); UUID key=UUID.randomUUID();
        var productBody=mapper.createObjectNode().put("productType","VACCINE").put("brandName","Race vaccine").put("stockUnit","vial").put("openingQuantity",10).put("openingUnitCost",5);
        var created=mvc.perform(post("/api/inventory/products").header("Authorization","Bearer "+f.token).contentType(MediaType.APPLICATION_JSON).content(productBody.toString())).andReturn().getResponse();
        long product=mapper.readTree(created.getContentAsString()).path("id").asLong();
        var payload=mapper.createObjectNode().put("quantity",2).put("purchaseUnitCost",5).put("stockDate",f.day).put("supplier","supplier A");
        var body=payload.deepCopy().put("operationId",key.toString());
        var path="/api/inventory/products/"+product+"/stock-ins";
        try (var pool=java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var gate=new java.util.concurrent.CountDownLatch(1);
            var write=pool.submit(()->{gate.await();return mvc.perform(post(path).header("Authorization","Bearer "+f.token).contentType(MediaType.APPLICATION_JSON).content(body.toString())).andReturn().getResponse();});
            var cancel=pool.submit(()->{gate.await();return resolveManager(f,"STOCK_IN",Long.toString(product),key,payload);});
            gate.countDown();var written=write.get(20,java.util.concurrent.TimeUnit.SECONDS);var resolved=cancel.get(20,java.util.concurrent.TimeUnit.SECONDS);
            assertThat(resolved.getStatus()).isEqualTo(200);
            String state=mapper.readTree(resolved.getContentAsString()).path("status").asText();
            assertThat(state).isIn("APPLIED","CANCELLED");
            assertThat(written.getStatus()).isEqualTo(state.equals("APPLIED")?200:409);
            int count=jdbc.queryForObject("SELECT count(*) FROM inventory_movement WHERE operation_id=?",Integer.class,key);
            assertThat(count).isEqualTo(state.equals("APPLIED")?1:0);
            assertThat(jdbc.queryForObject("SELECT stock_on_hand FROM farm_product WHERE id=?",java.math.BigDecimal.class,product)).isEqualByComparingTo(state.equals("APPLIED")?"12":"10");
        }
    }

    @Test void productCancellationAndStockReplayBindEveryRequestDetailAndActor() throws Exception {
        var f=fixture(); var other=fixture(); UUID productKey=UUID.randomUUID();
        var productBody=mapper.createObjectNode().put("productType","VACCINE").put("brandName","Exact vaccine").put("stockUnit","vial").put("openingQuantity",10).put("openingUnitCost",5);
        assertThat(resolveManager(f,"PRODUCT_CREATE","farm",productKey,productBody).getStatus()).isEqualTo(200);
        assertThat(mvc.perform(post("/api/inventory/products").header("Authorization","Bearer "+f.token).header("Idempotency-Key",productKey)
                .contentType(MediaType.APPLICATION_JSON).content(productBody.toString())).andReturn().getResponse().getStatus()).isEqualTo(409);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM farm_product WHERE farm_id=? AND brand_name='Exact vaccine'",Integer.class,f.farm)).isZero();
        var product=mvc.perform(post("/api/inventory/products").header("Authorization","Bearer "+f.token)
                .contentType(MediaType.APPLICATION_JSON).content(productBody.toString())).andReturn().getResponse();
        long productId=mapper.readTree(product.getContentAsString()).path("id").asLong();
        UUID stockId=UUID.randomUUID();var stock=mapper.createObjectNode().put("quantity",2).put("purchaseUnitCost",0).put("freeOfCharge",true).put("supplier","Donor A");
        var path="/api/inventory/products/"+productId+"/stock-ins";
        var write=stock.deepCopy().put("operationId",stockId.toString());
        assertThat(mvc.perform(post(path).header("Authorization","Bearer "+f.token).contentType(MediaType.APPLICATION_JSON).content(write.toString())).andReturn().getResponse().getStatus()).isEqualTo(200);
        assertThat(mvc.perform(post(path).header("Authorization","Bearer "+f.token).contentType(MediaType.APPLICATION_JSON).content(write.toString())).andReturn().getResponse().getStatus()).isEqualTo(200);
        assertThat(mapper.readTree(resolveManager(f,"STOCK_IN",Long.toString(productId),stockId,stock).getContentAsString()).path("status").asText()).isEqualTo("APPLIED");
        assertThat(mvc.perform(post(path).header("Authorization","Bearer "+f.token).contentType(MediaType.APPLICATION_JSON).content(write.deepCopy().put("supplier","Donor B").toString())).andReturn().getResponse().getStatus()).isEqualTo(409);
        assertThat(mvc.perform(post(path).header("Authorization","Bearer "+f.token).contentType(MediaType.APPLICATION_JSON).content(write.deepCopy().put("stockDate",f.day).toString())).andReturn().getResponse().getStatus()).isEqualTo(409);
        jdbc.update("UPDATE app_user SET farm_id=? WHERE id=?",f.farm,other.user);
        assertThat(mvc.perform(post(path).header("Authorization","Bearer "+other.token).contentType(MediaType.APPLICATION_JSON).content(write.toString())).andReturn().getResponse().getStatus()).isEqualTo(409);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM inventory_movement WHERE operation_id=?",Integer.class,stockId)).isEqualTo(1);
    }

    @Test void adjustmentsAndCorrectionsCanBeCancelledAndNeverAppliedLater() throws Exception {
        var f=fixture(); UUID adjustId=UUID.randomUUID();
        var productBody=mapper.createObjectNode().put("productType","OTHER").put("brandName","Adjustment item").put("stockUnit","bag").put("openingQuantity",10).put("openingUnitCost",5);
        var product=mvc.perform(post("/api/inventory/products").header("Authorization","Bearer "+f.token).contentType(MediaType.APPLICATION_JSON).content(productBody.toString())).andReturn().getResponse();
        long productId=mapper.readTree(product.getContentAsString()).path("id").asLong();
        var adjustment=mapper.createObjectNode().put("quantityDelta",2).put("reason","Physical count");
        assertThat(mapper.readTree(resolveManager(f,"STOCK_ADJUSTMENT",Long.toString(productId),adjustId,adjustment).getContentAsString()).path("status").asText()).isEqualTo("CANCELLED");
        var adjustmentWrite=adjustment.deepCopy().put("operationId",adjustId.toString());
        assertThat(mvc.perform(post("/api/inventory/products/"+productId+"/adjustments").header("Authorization","Bearer "+f.token).contentType(MediaType.APPLICATION_JSON).content(adjustmentWrite.toString())).andReturn().getResponse().getStatus()).isEqualTo(409);
        assertThat(mapper.readTree(resolveManager(f,"STOCK_ADJUSTMENT",Long.toString(productId),adjustId,adjustment).getContentAsString()).path("status").asText()).isEqualTo("CANCELLED");
        assertThat(jdbc.queryForObject("SELECT stock_on_hand FROM farm_product WHERE id=?",java.math.BigDecimal.class,productId)).isEqualByComparingTo("10");
        var expense=mapper.createObjectNode().put("batchId",f.batch).put("transactionDate",f.day).put("type","EXPENSE").put("category","Transport").put("amount",100);
        var original=mvc.perform(post("/api/financial-transactions").header("Authorization","Bearer "+f.token).contentType(MediaType.APPLICATION_JSON).content(expense.toString())).andReturn().getResponse();
        var originalJson=mapper.readTree(original.getContentAsString()); long originalId=originalJson.path("id").asLong(); UUID correctionId=UUID.randomUUID();
        var correction=mapper.createObjectNode().put("transactionDate",f.day).put("category","Transport").put("amount",120).put("reason","Correct price").put("expectedUpdatedAt",originalJson.path("updatedAt").asText());
        assertThat(mapper.readTree(resolveManager(f,"FINANCE_CORRECTION",Long.toString(originalId),correctionId,correction).getContentAsString()).path("status").asText()).isEqualTo("CANCELLED");
        assertThat(mvc.perform(post("/api/financial-transactions/"+originalId+"/corrections").header("Authorization","Bearer "+f.token).contentType(MediaType.APPLICATION_JSON).content(correction.deepCopy().put("operationId",correctionId.toString()).toString())).andReturn().getResponse().getStatus()).isEqualTo(409);
        assertThat(mapper.readTree(resolveManager(f,"FINANCE_CORRECTION",Long.toString(originalId),correctionId,correction).getContentAsString()).path("status").asText()).isEqualTo("CANCELLED");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM farm_financial_transaction WHERE correction_of_id=?",Integer.class,originalId)).isZero();
        assertThat(jdbc.queryForObject("SELECT amount FROM farm_financial_transaction WHERE id=?",java.math.BigDecimal.class,originalId)).isEqualByComparingTo("100");
    }

    @Test void legacyStockEvidenceCannotBeCancelledOrReappliedWithoutItsOriginalReceipt() throws Exception {
        var f=fixture(); UUID legacy=UUID.randomUUID();
        var productBody=mapper.createObjectNode().put("productType","VACCINE").put("brandName","Legacy item").put("stockUnit","vial").put("openingQuantity",10).put("openingUnitCost",5);
        var product=mvc.perform(post("/api/inventory/products").header("Authorization","Bearer "+f.token).contentType(MediaType.APPLICATION_JSON).content(productBody.toString())).andReturn().getResponse();
        long productId=mapper.readTree(product.getContentAsString()).path("id").asLong();
        var body=mapper.createObjectNode().put("quantity",2).put("purchaseUnitCost",0).put("freeOfCharge",true);
        var path="/api/inventory/products/"+productId+"/stock-ins";
        var existing=mvc.perform(post(path).header("Authorization","Bearer "+f.token).contentType(MediaType.APPLICATION_JSON).content(body.toString())).andReturn().getResponse();
        long movementId=mapper.readTree(existing.getContentAsString()).path("id").asLong();
        jdbc.update("UPDATE inventory_movement SET operation_id=? WHERE id=?",legacy,movementId);
        assertThat(resolveManager(f,"STOCK_IN",Long.toString(productId),legacy,body).getStatus()).isEqualTo(409);
        assertThat(mvc.perform(post(path).header("Authorization","Bearer "+f.token).contentType(MediaType.APPLICATION_JSON).content(body.deepCopy().put("operationId",legacy.toString()).toString())).andReturn().getResponse().getStatus()).isEqualTo(409);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM offline_command_receipt WHERE operation_id=?",Integer.class,legacy)).isZero();
        assertThat(jdbc.queryForObject("SELECT stock_on_hand FROM farm_product WHERE id=?",java.math.BigDecimal.class,productId)).isEqualByComparingTo("12");
    }

    @Test void managerResolutionRejectsHandlersAndMismatchedTargetsWithoutCreatingReceipts() throws Exception {
        var f=fixture();UUID key=UUID.randomUUID();
        var payload=mapper.createObjectNode().put("batchId",f.batch).put("transactionDate",f.day).put("type","EXPENSE").put("category","Transport").put("amount",100);
        var request=mapper.writeValueAsString(Map.of("kind","FINANCE_CREATE","targetId",Long.toString(f.batch),"operationId",key.toString(),"payload",payload));
        var anonymous=mvc.perform(post("/api/manager-operations/resolve").contentType(MediaType.APPLICATION_JSON).content(request)).andReturn().getResponse();
        assertThat(anonymous.getStatus()).isIn(401,403);
        var handler=mvc.perform(post("/api/manager-operations/resolve").with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("handler").roles("HANDLER"))
                .contentType(MediaType.APPLICATION_JSON).content(request)).andReturn().getResponse();
        assertThat(handler.getStatus()).isEqualTo(403);
        assertThat(resolveManager(f,"FINANCE_CREATE","farm",key,payload).getStatus()).isEqualTo(400);
        assertThat(resolveManager(f,"STOCK_IN","not-a-product",key,payload).getStatus()).isEqualTo(400);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM offline_command_receipt WHERE operation_id=?",Integer.class,key)).isZero();
    }

    @Test void oldQueuedAdjustmentCanApplyOnceButLegacyEffectsAreNotGuessed() throws Exception {
        var f=fixture();var other=fixture();var productBody=mapper.createObjectNode().put("productType","OTHER").put("brandName","Legacy adjustment item").put("stockUnit","bag").put("openingQuantity",10).put("openingUnitCost",5);
        var product=mvc.perform(post("/api/inventory/products").header("Authorization","Bearer "+f.token).contentType(MediaType.APPLICATION_JSON).content(productBody.toString())).andReturn().getResponse();
        long productId=mapper.readTree(product.getContentAsString()).path("id").asLong();
        UUID queued=UUID.randomUUID();var adjustment=mapper.createObjectNode().put("quantityDelta",2).put("reason","Count check");
        var path="/api/inventory/products/"+productId+"/adjustments";
        var body=adjustment.deepCopy().put("operationId",queued.toString());
        var first=mvc.perform(post(path).header("Authorization","Bearer "+f.token).contentType(MediaType.APPLICATION_JSON).content(body.toString())).andReturn().getResponse();
        var replay=mvc.perform(post(path).header("Authorization","Bearer "+f.token).contentType(MediaType.APPLICATION_JSON).content(body.toString())).andReturn().getResponse();
        assertThat(first.getStatus()).isEqualTo(200);assertThat(replay.getStatus()).isEqualTo(200);
        assertThat(mapper.readTree(first.getContentAsString()).path("id")).isEqualTo(mapper.readTree(replay.getContentAsString()).path("id"));
        assertThat(mapper.readTree(resolveManager(f,"STOCK_ADJUSTMENT",Long.toString(productId),queued,adjustment).getContentAsString()).path("status").asText()).isEqualTo("APPLIED");
        assertThat(mvc.perform(post(path).header("Authorization","Bearer "+f.token).contentType(MediaType.APPLICATION_JSON).content(body.deepCopy().put("occurredOn",f.day).toString())).andReturn().getResponse().getStatus()).isEqualTo(409);
        assertThat(mvc.perform(post(path).header("Authorization","Bearer "+f.token).contentType(MediaType.APPLICATION_JSON).content(body.deepCopy().put("reason","Different count").toString())).andReturn().getResponse().getStatus()).isEqualTo(409);
        jdbc.update("UPDATE app_user SET farm_id=? WHERE id=?",f.farm,other.user);
        assertThat(mvc.perform(post(path).header("Authorization","Bearer "+other.token).contentType(MediaType.APPLICATION_JSON).content(body.toString())).andReturn().getResponse().getStatus()).isEqualTo(409);
        UUID legacy=UUID.randomUUID();
        var legacyBody=mapper.treeToValue(adjustment.deepCopy().put("operationId",legacy.toString()),com.poultryprophet.inventory.dto.InventoryAdjustmentRequest.class);
        context.getBean(com.poultryprophet.inventory.InventoryService.class).adjust(f.farm,f.user,productId,legacyBody);
        assertThat(resolveManager(f,"STOCK_ADJUSTMENT",Long.toString(productId),legacy,adjustment).getStatus()).isEqualTo(409);
        assertThat(mvc.perform(post(path).header("Authorization","Bearer "+f.token).contentType(MediaType.APPLICATION_JSON).content(adjustment.deepCopy().put("operationId",legacy.toString()).toString())).andReturn().getResponse().getStatus()).isEqualTo(409);
        assertThat(jdbc.queryForObject("SELECT stock_on_hand FROM farm_product WHERE id=?",java.math.BigDecimal.class,productId)).isEqualByComparingTo("14");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM offline_command_receipt WHERE operation_id=?",Integer.class,legacy)).isZero();
    }

    @Test void legacyCorrectionWithoutReceiptNeedsReviewRatherThanInventedCancellation() throws Exception {
        var f=fixture();var expense=mapper.createObjectNode().put("batchId",f.batch).put("transactionDate",f.day).put("type","EXPENSE").put("category","Transport").put("amount",100);
        var created=mvc.perform(post("/api/financial-transactions").header("Authorization","Bearer "+f.token).contentType(MediaType.APPLICATION_JSON).content(expense.toString())).andReturn().getResponse();
        var original=mapper.readTree(created.getContentAsString());long id=original.path("id").asLong();UUID legacy=UUID.randomUUID();
        var correction=mapper.createObjectNode().put("transactionDate",f.day).put("category","Transport").put("amount",120).put("reason","Old correction").put("expectedUpdatedAt",original.path("updatedAt").asText());
        var legacyRequest=mapper.treeToValue(correction.deepCopy().put("operationId",legacy.toString()),com.poultryprophet.finance.dto.CorrectExpenseRequest.class);
        context.getBean(com.poultryprophet.finance.FinanceService.class).correctExpense(id,f.farm,f.user,legacyRequest);
        assertThat(resolveManager(f,"FINANCE_CORRECTION",Long.toString(id),legacy,correction).getStatus()).isEqualTo(409);
        assertThat(mvc.perform(post("/api/financial-transactions/"+id+"/corrections").header("Authorization","Bearer "+f.token).contentType(MediaType.APPLICATION_JSON).content(correction.deepCopy().put("operationId",legacy.toString()).toString())).andReturn().getResponse().getStatus()).isEqualTo(409);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM farm_financial_transaction WHERE correction_of_id=?",Integer.class,id)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM offline_command_receipt WHERE operation_id=?",Integer.class,legacy)).isZero();
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
