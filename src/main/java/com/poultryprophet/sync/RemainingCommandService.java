package com.poultryprophet.sync;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.poultryprophet.event.BatchEventService;
import com.poultryprophet.event.dto.*;
import com.poultryprophet.sync.dto.SyncOperationResult;
import org.springframework.stereotype.Service;
import java.time.*;
import java.util.UUID;
import java.util.function.*;

/** Typed online/v2 boundaries; domain callbacks retain their existing locks and validation. */
@Service
public class RemainingCommandService {
    public record StoredOutcome(JsonNode response, Long serverId, Instant appliedAt) {}
    public record Result<T>(T response, SyncOperationResult acknowledgement) {}
    private final CommandReceiptService receipts;
    private final ObjectMapper mapper;
    private final Clock clock;
    private final BatchEventService events;
    private final com.poultryprophet.input.FarmInputService inputs;
    private final com.poultryprophet.sexcomposition.BatchSexCompositionService sex;
    private final com.poultryprophet.selectionsession.SelectionSessionService selections;
    private final com.poultryprophet.task.TaskService tasks;
    private final com.poultryprophet.vaccination.VaccinationService vaccinations;
    public RemainingCommandService(CommandReceiptService receipts, ObjectMapper mapper, Clock clock, BatchEventService events, com.poultryprophet.input.FarmInputService inputs, com.poultryprophet.sexcomposition.BatchSexCompositionService sex, com.poultryprophet.selectionsession.SelectionSessionService selections, com.poultryprophet.task.TaskService tasks, com.poultryprophet.vaccination.VaccinationService vaccinations) {
        this.receipts=receipts; this.mapper=mapper; this.clock=clock; this.events=events; this.inputs=inputs; this.sex=sex; this.selections=selections; this.tasks=tasks; this.vaccinations=vaccinations;
    }
    private <T> Result<T> execute(UUID id,Long farm,Long user,String type,String resource,Long resourceId,
                                  ObjectNode payload,Class<T> resultType,Supplier<T> apply,Function<T,Long> serverId) {
        var execution=receipts.executeOnce(new CommandReceiptService.CommandIdentity(id,1,farm,user,type,resource,resourceId),
                payload,StoredOutcome.class,()->{
                    T saved=apply.get(); return new StoredOutcome(mapper.valueToTree(saved),serverId.apply(saved),clock.instant());
                });
        var outcome=execution.outcome();
        try {
            return new Result<>(mapper.treeToValue(outcome.response(),resultType),new SyncOperationResult(id,
                    execution.replayed()?"ALREADY_APPLIED":"APPLIED",outcome.serverId(),outcome.appliedAt(),null));
        } catch (com.fasterxml.jackson.core.JsonProcessingException ex) { throw new IllegalStateException("Stored command response is invalid",ex); }
    }
    public Result<BatchEventResponse> event(Long batch,Long farm,Long user,CreateBatchEventRequest request,Instant envelopeTime) {
        ObjectNode payload=mapper.valueToTree(request);
        return execute(request.operationId(),farm,user,"BATCH_EVENT","BATCH",batch,payload,BatchEventResponse.class,()->{
            CreateBatchEventRequest effective=request;
            if(request.eventDate()==null && envelopeTime!=null) effective=new CreateBatchEventRequest(
                    envelopeTime.atZone(clock.getZone()).toLocalDate(), request.eventType(), request.title(),request.severityLabel(),
                    request.affectedCount(),request.details(),request.tags(),request.operationId(),request.populationDelta(),request.sexAllocation(),
                    request.occurredAt()==null?envelopeTime:request.occurredAt(),request.saleDetails(),request.deathObservation(),request.deathSigns());
            return events.create(batch,farm,user,effective);
        },BatchEventResponse::id);
    }
    public Result<com.poultryprophet.input.dto.FarmInputLogResponse> input(Long farm,Long user,com.poultryprophet.user.Role role,
            com.poultryprophet.input.dto.CreateFarmInputRequest request,Instant envelopeTime) {
        ObjectNode payload=mapper.valueToTree(request);
        String resource=request.batchId()!=null?"BATCH":"INCUBATION_CYCLE";
        Long resourceId=request.batchId()!=null?request.batchId():request.incubationCycleId();
        return execute(request.operationId(),farm,user,"FARM_INPUT",resource,resourceId,payload,
                com.poultryprophet.input.dto.FarmInputLogResponse.class,()->{
            var effective=request;
            if(request.recordedAt()==null && envelopeTime!=null) effective=new com.poultryprophet.input.dto.CreateFarmInputRequest(
                    request.batchId(),request.incubationCycleId(),envelopeTime,request.productType(),request.brandName(),request.productName(),
                    request.quantity(),request.unit(),request.route(),request.purpose(),request.notes(),request.operationId(),request.farmProductId(),request.affectedBirdCount());
            return inputs.create(farm,user,role,effective);
        },com.poultryprophet.input.dto.FarmInputLogResponse::id);
    }

    public Result<com.poultryprophet.sexcomposition.dto.SexCompositionResponse> composition(Long batch,Long farm,Long user,
            com.poultryprophet.sexcomposition.dto.CreateSexCompositionRequest request) {
        return execute(request.operationId(),farm,user,"SEX_COMPOSITION","BATCH",batch,mapper.valueToTree(request),
                com.poultryprophet.sexcomposition.dto.SexCompositionResponse.class,()->sex.record(batch,farm,user,request),
                com.poultryprophet.sexcomposition.dto.SexCompositionResponse::id);
    }

    public Result<com.poultryprophet.selectionsession.SelectionSessionResponse> selection(Long batch,Long farm,Long user,
            com.poultryprophet.selectionsession.CreateSelectionSessionRequest request,Long supersedesId) {
        ObjectNode payload=mapper.valueToTree(request);
        if(supersedesId==null) payload.putNull("supersedesSessionId"); else payload.put("supersedesSessionId",supersedesId);
        var codes=mapper.createArrayNode();
        if(request.criterionCodes()!=null) request.criterionCodes().stream().filter(java.util.Objects::nonNull).map(String::trim)
                .filter(value->!value.isEmpty()).map(value->value.toUpperCase(java.util.Locale.ROOT)).distinct().sorted().forEach(codes::add);
        payload.set("criterionCodes",codes);
        return execute(request.operationId(),farm,user,"SELECTION_SESSION","BATCH",batch,payload,
                com.poultryprophet.selectionsession.SelectionSessionResponse.class,
                ()->supersedesId==null?selections.create(batch,farm,user,request,null):selections.supersede(batch,farm,supersedesId,user,request),
                com.poultryprophet.selectionsession.SelectionSessionResponse::id);
    }

    public Result<com.poultryprophet.task.dto.TaskResponse> task(Long id,Long farm,Long user,boolean manager,
            com.poultryprophet.task.dto.UpdateTaskStatusRequest request,Long expectedBatch) {
        // Current task visibility is authorization, not a new-write lifecycle rule.
        // Check it before disclosing a stored task response, including on replay.
        tasks.get(id,farm,user,manager);
        var result=execute(request.operationId(),farm,user,"TASK_STATUS","TASK",id,mapper.valueToTree(request),
                com.poultryprophet.task.dto.TaskResponse.class,()->{
            var saved=tasks.updateStatus(id,farm,user,manager,request);checkBatch(expectedBatch,saved.batchId());return saved;
        },com.poultryprophet.task.dto.TaskResponse::id);
        checkBatch(expectedBatch,result.response().batchId());return result;
    }
    private void checkBatch(Long expected,Long actual) {
        if(expected!=null && !java.util.Objects.equals(expected,actual==null?0L:actual))
            throw new com.poultryprophet.common.ConflictException("The operation batch does not match its resource.");
    }

    public Result<com.poultryprophet.vaccination.dto.VaccinationPlanItemResponse> vaccination(Long plan,Long farm,Long user,
            com.poultryprophet.user.Role role,com.poultryprophet.vaccination.dto.RecordVaccinationRequest request,Long expectedBatch,Instant envelopeTime) {
        var result=execute(request.operationId(),farm,user,"VACCINATION_PLAN","VACCINATION_PLAN_ITEM",plan,mapper.valueToTree(request),
                com.poultryprophet.vaccination.dto.VaccinationPlanItemResponse.class,()->{
            var effective=request;
            if(request.recordedAt()==null && envelopeTime!=null) effective=new com.poultryprophet.vaccination.dto.RecordVaccinationRequest(
                    envelopeTime,request.quantity(),request.unit(),request.notes(),request.operationId());
            var saved=vaccinations.record(plan,farm,user,role,effective);checkBatch(expectedBatch,saved.batchId());return saved;
        },com.poultryprophet.vaccination.dto.VaccinationPlanItemResponse::id);
        checkBatch(expectedBatch,result.response().batchId());return result;
    }

}
