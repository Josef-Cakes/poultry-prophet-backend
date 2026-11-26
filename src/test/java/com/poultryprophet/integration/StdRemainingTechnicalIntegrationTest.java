package com.poultryprophet.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockHttpServletResponse;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

/** Runs only against the disposable, loopback PostgreSQL validation cluster. */
@EnabledIfEnvironmentVariable(named="POULTRY_DISPOSABLE_DB_URL", matches="jdbc:postgresql://127\\.0\\.0\\.1:25432/poultry_prophet_validation")
@SpringBootTest(properties={"spring.config.import=", "app.test-lab.enabled=true", "app.offline.command-receipts-enabled=true"})
@AutoConfigureMockMvc
@ActiveProfiles("validation")
class StdRemainingTechnicalIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;
    @DynamicPropertySource
    static void database(DynamicPropertyRegistry properties) {
        String url = System.getenv("POULTRY_DISPOSABLE_DB_URL");
        if (!"jdbc:postgresql://127.0.0.1:25432/poultry_prophet_validation".equals(url)) throw new IllegalStateException("Disposable DB required before connecting");
        String schema = "technical_qa_" + UUID.randomUUID().toString().replace("-", "");
        var admin = new org.springframework.jdbc.datasource.DriverManagerDataSource(url, "poultry_validation", "local-disposable-only");
        new org.springframework.jdbc.core.JdbcTemplate(admin).execute("CREATE SCHEMA " + schema);
        String scoped = url + "?currentSchema=" + schema;
        new org.springframework.jdbc.datasource.init.ResourceDatabasePopulator(
                new org.springframework.core.io.ClassPathResource("db/reviewed-migration/V32__shared_command_receipts.sql"))
                .execute(new org.springframework.jdbc.datasource.DriverManagerDataSource(scoped, "poultry_validation", "local-disposable-only"));
        properties.add("spring.datasource.url", () -> scoped);
        properties.add("spring.datasource.username", () -> "poultry_validation");
        properties.add("spring.datasource.password", () -> "local-disposable-only");
        properties.add("poultry.security.jwt-secret", () -> "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=");
        properties.add("validation.manager.email", () -> "seed-manager@example.test");
        properties.add("validation.manager.password", () -> "seed-password-only");
        properties.add("validation.handler.email", () -> "seed-handler@example.test");
        properties.add("validation.handler.password", () -> "seed-password-only");
    }
    private MockHttpServletResponse postJson(String path, String token, Map<String,Object> payload) throws Exception {
        var request=post(path).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(payload));
        if (token!=null) request.header("Authorization","Bearer "+token);
        return mvc.perform(request).andReturn().getResponse();
    }
    private String register() throws Exception {
        String email="trace-"+UUID.randomUUID()+"@example.test";
        var res=postJson("/api/auth/register",null,Map.of("email",email,"password","test-password-123","fullName","Test manager"));
        assertThat(res.getStatus()).isEqualTo(201);
        return json.readTree(res.getContentAsString()).path("token").asText();
    }
    private Map<String,Object> batch(String name, int population) {
        String day=LocalDate.now(ZoneId.of("Asia/Manila")).toString();
        return Map.of("name",name,"initialPopulation",population,"bloodline","Test line","hatchDate",day,"farmEntryDate",day,"startDate",day);
    }
    private JsonNode getJson(String path, String token) throws Exception {
        var r=mvc.perform(get(path).header("Authorization","Bearer "+token)).andReturn().getResponse();
        assertThat(r.getStatus()).as(path).isEqualTo(200);return json.readTree(r.getContentAsString());
    }
    private long newBatch(String token,String name,int amount) throws Exception {
        var r=postJson("/api/batches",token,batch(name,amount));assertThat(r.getStatus()).isEqualTo(201);return json.readTree(r.getContentAsString()).path("id").asLong();
    }
    private Map<String,Object> event(String type,int count,String op) {
        String today=LocalDate.now(ZoneId.of("Asia/Manila")).toString();
        return Map.of("eventDate",today,"eventType",type,"title","Synthetic "+type,"affectedCount",count,"operationId",op,"details","No signs noticed.");
    }
    private JsonNode dashboardBatch(String token,long id) throws Exception {
        for(JsonNode x:getJson("/api/batches/dashboard",token)) if(x.path("batch").path("id").asLong()==id)return x;
        throw new AssertionError("batch absent from dashboard");
    }
    record Pair(String manager,String handler,long farm) {}
    private JsonNode account() throws Exception {
        var r=postJson("/api/auth/register",null,Map.of("email",UUID.randomUUID()+"@example.test","password","test-password-123","fullName","Synthetic reviewer"));
        assertThat(r.getStatus()).isEqualTo(201);return json.readTree(r.getContentAsString());
    }
    private Pair pair() throws Exception {
        var m=account();var h=account();long farm=m.path("farmId").asLong();
        jdbc.update("update app_user set role='HANDLER', farm_id=? where id=?",farm,h.path("userId").asLong());
        var login=postJson("/api/auth/login",null,Map.of("email",h.path("email").asText(),"password","test-password-123"));
        assertThat(login.getStatus()).isEqualTo(200);return new Pair(m.path("token").asText(),json.readTree(login.getContentAsString()).path("token").asText(),farm);
    }
    private String today(){return LocalDate.now(ZoneId.of("Asia/Manila")).toString();}
    private Map<String,Object> load(){return Map.of("cycleName","Synthetic hatch "+UUID.randomUUID(),"incubatorCode","TEST-01","bloodline","Test line","eggSource","Synthetic source","loadedDate",LocalDate.parse(today()).minusDays(21).toString(),"eggsLoaded",100);}
    private int patchJson(String path,String token,Map<String,Object> body) throws Exception{return mvc.perform(patch(path).header("Authorization","Bearer "+token).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body))).andReturn().getResponse().getStatus();}
    @Test void incubationLoadsCompletesAndCreatesExactlyOneBatchWithRoleAndBoundaryChecks() throws Exception {
        var p=pair();var body=load();
        assertThat(postJson("/api/incubation-cycles",p.manager,body).getStatus()).isEqualTo(403);
        var invalid=new java.util.HashMap<>(body);invalid.put("eggsLoaded",0);assertThat(postJson("/api/incubation-cycles",p.handler,invalid).getStatus()).isEqualTo(400);
        invalid=new java.util.HashMap<>(body);invalid.remove("incubatorCode");assertThat(postJson("/api/incubation-cycles",p.handler,invalid).getStatus()).isEqualTo(400);
        var created=postJson("/api/incubation-cycles",p.handler,body);assertThat(created.getStatus()).isEqualTo(201);long id=json.readTree(created.getContentAsString()).path("id").asLong();String path="/api/incubation-cycles/"+id;
        assertThat(patchJson(path,p.manager,Map.of("notes","Reviewed synthetic load"))).isEqualTo(200);
        var result=Map.<String,Object>of("actualHatchDate",today(),"hatchedCount",80,"unhatchedCount",15,"removedDamagedCount",5);
        assertThat(postJson(path+"/complete",p.handler,result).getStatus()).isEqualTo(403);
        var bad=new java.util.HashMap<>(result);bad.put("hatchedCount",81);assertThat(postJson(path+"/complete",p.manager,bad).getStatus()).isEqualTo(400);
        assertThat(getJson(path,p.manager).path("status").asText()).isNotEqualTo("COMPLETED");
        assertThat(postJson(path+"/complete",p.manager,result).getStatus()).isEqualTo(200);
        assertThat(patchJson(path,p.manager,Map.of("notes","Invalid late edit"))).isEqualTo(400);
        var request=Map.<String,Object>of("bloodline","Test line","farmEntryDate",today());
        var batch=postJson(path+"/create-batch",p.manager,request);assertThat(batch.getStatus()).isEqualTo(200);var b=json.readTree(batch.getContentAsString());long batchId=b.path("id").asLong();
        assertThat(b.path("initialPopulation").asInt()).isEqualTo(80);assertThat(b.path("hatchDate").asText()).isEqualTo(today());
        var retry=postJson(path+"/create-batch",p.manager,request);assertThat(retry.getStatus()).isEqualTo(200);assertThat(json.readTree(retry.getContentAsString()).path("id").asLong()).isEqualTo(batchId);
        assertThat(getJson(path,p.manager).path("createdBatchId").asLong()).isEqualTo(batchId);
    }
    @Test void cancelledIncubationRemainsRetrievableAndCannotBeEdited() throws Exception {
        var p=pair();var r=postJson("/api/incubation-cycles",p.handler,load());assertThat(r.getStatus()).isEqualTo(201);String path="/api/incubation-cycles/"+json.readTree(r.getContentAsString()).path("id").asLong();
        assertThat(postJson(path+"/cancel",p.manager,Map.of("reason","Synthetic cancellation")).getStatus()).isEqualTo(200);
        assertThat(getJson(path,p.manager).path("status").asText()).isEqualTo("CANCELLED");
        assertThat(patchJson(path,p.manager,Map.of("notes","Late edit"))).isEqualTo(400);
        String other=register();assertThat(mvc.perform(get(path).header("Authorization","Bearer "+other)).andReturn().getResponse().getStatus()).isEqualTo(404);
    }
    @Test void signedPopulationCorrectionPreservesHistoryAndEnforcesTransferMaximum() throws Exception {
        String token=register();long id=newBatch(token,"Correction synthetic flock",100);String path="/api/batches/"+id;
        assertThat(postJson(path+"/events",token,event("MISSING",10,UUID.randomUUID().toString())).getStatus()).isEqualTo(201);
        var correction=new java.util.HashMap<>(event("COUNT_CORRECTION",0,UUID.randomUUID().toString()));correction.put("populationDelta",-2);correction.put("details","Physical recount found two fewer birds.");
        var r=postJson(path+"/events",token,correction);assertThat(r.getStatus()).isEqualTo(201);long eventId=json.readTree(r.getContentAsString()).path("id").asLong();
        assertThat(getJson(path,token).path("currentPopulation").asInt()).isEqualTo(88);
        assertThat(getJson(path+"/events",token).size()).isEqualTo(2);
        assertThat(jdbc.queryForObject("select population_delta from batch_event where id=?",Integer.class,eventId)).isEqualTo(-2);
        assertThat(jdbc.queryForObject("select count(*) from batch_event where id=? and handler_id is not null and created_at is not null and details is not null",Integer.class,eventId)).isEqualTo(1);
        correction.put("populationDelta",13);correction.put("operationId",UUID.randomUUID().toString());assertThat(postJson(path+"/events",token,correction).getStatus()).isEqualTo(400);
        assertThat(getJson(path,token).path("currentPopulation").asInt()).isEqualTo(88);
    }
    private Map<String,Object> tally(long review,int accepted){return Map.of("selectionDate",today(),"evaluatedCount",5,"acceptedCount",accepted,"continueObservationCount",5-accepted,"notAcceptedCount",0,"otherCount",0,"selectionReviewId",review,"sourceDataAcknowledged",true,"criterionCodes",java.util.List.of());}
    @Test void selectionFinalizeAndSupersedeKeepPopulationAndOriginalHistory() throws Exception {
        String token=register();long id=newBatch(token,"Selection synthetic flock",20);String path="/api/batches/"+id;int eventsBefore=jdbc.queryForObject("select count(*) from batch_event where batch_id=?",Integer.class,id);
        var created=postJson(path+"/selection-reviews",token,Map.of("idempotencyKey",UUID.randomUUID().toString()));assertThat(created.getStatus()).isEqualTo(201);long review=json.readTree(created.getContentAsString()).path("id").asLong();
        assertThat(postJson(path+"/selection-reviews/"+review+"/finalize",token,Map.of("reviewStatus","FOR_IN_PERSON_ASSESSMENT","managerNotes","Synthetic review only")).getStatus()).isEqualTo(200);
        var initial=postJson(path+"/selection-sessions",token,tally(review,4));assertThat(initial.getStatus()).isEqualTo(200);long session=json.readTree(initial.getContentAsString()).path("id").asLong();
        assertThat(postJson(path+"/selection-sessions/"+session+"/finalize",token,Map.of()).getStatus()).isEqualTo(200);
        var replacement=postJson(path+"/selection-sessions/"+session+"/supersede",token,tally(review,3));assertThat(replacement.getStatus()).isEqualTo(200);long newer=json.readTree(replacement.getContentAsString()).path("id").asLong();
        assertThat(newer).isNotEqualTo(session);
        assertThat(getJson(path+"/selection-sessions/"+session,token).path("status").asText()).isEqualTo("FINALIZED");
        assertThat(postJson(path+"/selection-sessions/"+newer+"/finalize",token,Map.of()).getStatus()).isEqualTo(200);
        assertThat(getJson(path+"/selection-sessions/"+session,token).path("status").asText()).isEqualTo("SUPERSEDED");
        assertThat(getJson(path+"/selection-sessions",token).size()).isEqualTo(2);
        assertThat(getJson(path,token).path("currentPopulation").asInt()).isEqualTo(20);
        assertThat(jdbc.queryForObject("select count(*) from batch_event where batch_id=?",Integer.class,id)).isEqualTo(eventsBefore);
    }
    @Test void testLabIsManagerOnlyLabelsSyntheticCopyAndLeavesSourceUntouched() throws Exception {
        var p=pair();long id=newBatch(p.manager,"Original synthetic fixture",150);String path="/api/batches/"+id;
        assertThat(mvc.perform(get("/api/test-lab/status").header("Authorization","Bearer "+p.handler)).andReturn().getResponse().getStatus()).isEqualTo(403);
        assertThat(getJson("/api/test-lab/status",p.manager).path("enabled").asBoolean()).isTrue();
        var request=Map.<String,Object>of("sourceBatchId",id,"seed",20261122,"confirmation","CREATE_TEST_COPY");
        assertThat(postJson("/api/test-lab/generate",p.handler,request).getStatus()).isEqualTo(403);
        var r=postJson("/api/test-lab/generate",p.manager,request);assertThat(r.getStatus()).as(r.getContentAsString()).isEqualTo(201);var generated=json.readTree(r.getContentAsString());
        assertThat(generated.path("batchId").asLong()).isNotEqualTo(id);assertThat(generated.path("batchName").asText()).startsWith("[TEST COPY]");
        assertThat(getJson(path,p.manager).path("currentPopulation").asInt()).isEqualTo(150);
        assertThat(jdbc.queryForObject("select count(*) from batch_event where batch_id=?",Integer.class,id)).isZero();
        String otherManager=register();
        long otherSource=newBatch(otherManager,"Other original synthetic fixture",150);
        var repeatedSeed=postJson("/api/test-lab/generate",otherManager,Map.of("sourceBatchId",otherSource,"seed",20261122,"confirmation","CREATE_TEST_COPY"));
        assertThat(repeatedSeed.getStatus()).as(repeatedSeed.getContentAsString()).isEqualTo(201);
        long otherCopy=json.readTree(repeatedSeed.getContentAsString()).path("batchId").asLong();
        assertThat(otherCopy).isNotEqualTo(generated.path("batchId").asLong());
        assertThat(jdbc.queryForObject("select count(*) from batch_event where batch_id=?",Integer.class,otherCopy)).isGreaterThan(0);
    }
}
