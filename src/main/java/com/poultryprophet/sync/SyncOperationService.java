package com.poultryprophet.sync;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.poultryprophet.common.BadRequestException;
import com.poultryprophet.common.ConflictException;
import com.poultryprophet.common.NotFoundException;
import com.poultryprophet.record.DailyRecordService;
import com.poultryprophet.record.dto.CreateRecordRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import com.poultryprophet.event.BatchEventRepository;
import com.poultryprophet.event.BatchEventService;
import com.poultryprophet.event.dto.BatchEventResponse;
import com.poultryprophet.event.dto.CreateBatchEventRequest;
import com.poultryprophet.input.FarmInputLogRepository;
import com.poultryprophet.input.FarmInputService;
import com.poultryprophet.input.dto.CreateFarmInputRequest;
import com.poultryprophet.input.dto.FarmInputLogResponse;
import com.poultryprophet.selectionsession.BatchSelectionSessionRepository;
import com.poultryprophet.selectionsession.CreateSelectionSessionRequest;
import com.poultryprophet.selectionsession.SelectionSessionResponse;
import com.poultryprophet.selectionsession.SelectionSessionService;
import com.poultryprophet.sexcomposition.BatchSexCompositionService;
import com.poultryprophet.sexcomposition.dto.CreateSexCompositionRequest;
import com.poultryprophet.sexcomposition.dto.SexCompositionResponse;
import com.poultryprophet.sync.dto.SyncOperationRequest;
import com.poultryprophet.sync.dto.SyncOperationResult;
import com.poultryprophet.sync.dto.SyncOperationsRequest;
import com.poultryprophet.sync.dto.SyncOperationsResponse;
import com.poultryprophet.user.Role;
import com.poultryprophet.vaccination.VaccinationService;
import com.poultryprophet.vaccination.dto.RecordVaccinationRequest;
import com.poultryprophet.vaccination.dto.VaccinationPlanItemResponse;
import com.poultryprophet.vaccination.dto.CreateVaccinationProgramRequest;
import com.poultryprophet.vaccination.dto.VaccinationProgramResponse;
import com.poultryprophet.task.TaskService;
import com.poultryprophet.task.dto.TaskResponse;
import com.poultryprophet.task.dto.UpdateTaskStatusRequest;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.springframework.beans.factory.annotation.Autowired;
import java.time.Clock;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Versioned replay boundary for the browser outbox. */
@Service
public class SyncOperationService {
    private static final int MAX_OPERATIONS = 25;

    private final ObjectMapper objectMapper;
    private final BatchEventService eventService;
    private final FarmInputService inputService;
    private final BatchEventRepository eventRepository;
    private final FarmInputLogRepository inputRepository;
    private final SelectionSessionService selectionSessionService;
    private final BatchSelectionSessionRepository selectionSessionRepository;
    private final Validator validator;
    private final BatchSexCompositionService sexCompositionService;
    private final VaccinationService vaccinationService;
    private final TaskService taskService;
    private final OfflineSyncReceiptRepository receiptRepository;
    private final Clock clock;
    private final TransactionTemplate transactionTemplate;
    private final DailyRecordService dailyRecordService;
    private final JdbcTemplate jdbcTemplate;
    private final com.poultryprophet.selectionsession.SelectionSessionCommandService selectionCommands;
    private final CommandReceiptService commandReceipts;
    private final RemainingCommandService remainingCommands;

    @Autowired
    public SyncOperationService(ObjectMapper objectMapper, BatchEventService eventService,
                                FarmInputService inputService, BatchEventRepository eventRepository,
                                FarmInputLogRepository inputRepository,
                                SelectionSessionService selectionSessionService,
                                BatchSelectionSessionRepository selectionSessionRepository,
                                Validator validator,
                                BatchSexCompositionService sexCompositionService,
                                VaccinationService vaccinationService,
                                TaskService taskService,
                                OfflineSyncReceiptRepository receiptRepository,
                                Clock clock, PlatformTransactionManager transactionManager,
                                DailyRecordService dailyRecordService, JdbcTemplate jdbcTemplate,
                                com.poultryprophet.selectionsession.SelectionSessionCommandService selectionCommands,
                                CommandReceiptService commandReceipts, RemainingCommandService remainingCommands) {
        this.objectMapper = objectMapper;
        this.eventService = eventService;
        this.inputService = inputService;
        this.eventRepository = eventRepository;
        this.inputRepository = inputRepository;
        this.selectionSessionService = selectionSessionService;
        this.selectionSessionRepository = selectionSessionRepository;
        this.validator = validator;
        this.sexCompositionService = sexCompositionService;
        this.vaccinationService = vaccinationService;
        this.taskService = taskService;
        this.receiptRepository = receiptRepository;
        this.clock = clock;
        this.transactionTemplate = transactionManager == null ? null : new TransactionTemplate(transactionManager);
        this.dailyRecordService = dailyRecordService;
        this.jdbcTemplate = jdbcTemplate;
        this.selectionCommands = selectionCommands;
        this.commandReceipts = commandReceipts;
        this.remainingCommands = remainingCommands;
    }

    /** Compatibility constructor for focused sync tests that do not exercise sex or vaccination replay. */
    public SyncOperationService(ObjectMapper objectMapper, BatchEventService eventService,
                                FarmInputService inputService, BatchEventRepository eventRepository,
                                FarmInputLogRepository inputRepository,
                                SelectionSessionService selectionSessionService,
                                BatchSelectionSessionRepository selectionSessionRepository,
                                Validator validator) {
        this(objectMapper, eventService, inputService, eventRepository, inputRepository,
                selectionSessionService, selectionSessionRepository, validator, null, null, null, null,
                Clock.system(ZoneId.of("Asia/Manila")), null, null, null, null, null, null);
    }

    public SyncOperationsResponse sync(SyncOperationsRequest request, Long farmId, Long userId, Role role) {
        if (request.operations().size() > MAX_OPERATIONS) {
            throw new BadRequestException("Send at most " + MAX_OPERATIONS + " operations at a time");
        }
        List<SyncOperationResult> results = new ArrayList<>();
        int applied = 0, conflicts = 0, rejected = 0, retryable = 0;
        Set<Long> blockedBatches = new java.util.HashSet<>();
        for (SyncOperationRequest operation : request.operations()) {
            if (blockedBatches.contains(operation.batchId())) {
                results.add(SyncOperationResult.retryable(operation.operationId(), "An earlier record for this batch needs attention first."));
                retryable++;
                continue;
            }
            try {
                SyncOperationResult result = transactionTemplate == null
                        ? apply(operation, farmId, userId, role)
                        : transactionTemplate.execute(status -> apply(operation, farmId, userId, role));
                results.add(java.util.Objects.requireNonNull(result));
                applied++;
            } catch (CommandReceiptsUnavailableException ex) {
                blockedBatches.add(operation.batchId());
                results.add(SyncOperationResult.retryable(operation.operationId(), ex.getMessage()));
                retryable++;
            } catch (ConflictException ex) {
                blockedBatches.add(operation.batchId());
                results.add(SyncOperationResult.conflict(operation.operationId(), ex.getMessage()));
                conflicts++;
            } catch (BadRequestException | NotFoundException ex) {
                blockedBatches.add(operation.batchId());
                results.add(SyncOperationResult.rejected(operation.operationId(), ex.getMessage()));
                rejected++;
            } catch (RuntimeException ex) {
                blockedBatches.add(operation.batchId());
                results.add(SyncOperationResult.retryable(operation.operationId(),
                        "The server could not finish this record yet."));
                retryable++;
            }
        }
        return new SyncOperationsResponse(request.operations().size(), applied, conflicts, rejected, retryable, results);
    }

    private SyncOperationResult apply(SyncOperationRequest operation, Long farmId, Long userId, Role role) {
        if (operation.schemaVersion() != 1) {
            throw new BadRequestException("Unsupported offline operation schema version: " + operation.schemaVersion());
        }
        return switch (operation.entityType()) {
            case "DAILY_RECORD" -> dailyCommand(operation, farmId, userId);
            case "BATCH_EVENT" -> {
                CreateBatchEventRequest payload = readPayload(operation, CreateBatchEventRequest.class, "batch event");
                validatePayload(payload, "batch event");
                if (!operation.operationId().equals(payload.operationId())) throw new BadRequestException("The operation ID does not match the event payload");
                yield remainingCommands.event(operation.batchId(),farmId,userId,payload,operation.occurredAt()).acknowledgement();
            }
            case "FARM_INPUT" -> {
                CreateFarmInputRequest payload=readPayload(operation,CreateFarmInputRequest.class,"farm input");
                validatePayload(payload,"farm input");
                if(!operation.operationId().equals(payload.operationId())) throw new BadRequestException("The operation ID does not match the input payload");
                if(!operation.batchId().equals(payload.batchId())) throw new BadRequestException("The operation batch does not match the input payload");
                yield remainingCommands.input(farmId,userId,role,payload,operation.occurredAt()).acknowledgement();
            }
            case "SELECTION_SESSION" -> {
                requireManager(role);
                CreateSelectionSessionRequest payload = readPayload(operation, CreateSelectionSessionRequest.class, "selection session");
                validatePayload(payload, "selection session");
                if (!operation.operationId().equals(payload.operationId())) {
                    throw new BadRequestException("The operation ID does not match the selection session payload");
                }
                yield remainingCommands.selection(operation.batchId(),farmId,userId,payload,null).acknowledgement();
            }
            case "SELECTION_SESSION_UPDATE" -> {
                requireManager(role);
                if (operation.payload() == null || !operation.payload().isObject()
                        || !operation.payload().hasNonNull("sessionId")
                        || !operation.payload().get("sessionId").isIntegralNumber()
                        || !operation.payload().get("sessionId").canConvertToLong()
                        || operation.payload().get("sessionId").asLong() < 1) {
                    throw new BadRequestException("The selection session update is missing its session ID");
                }
                long sessionId = operation.payload().get("sessionId").asLong();
                ObjectNode updatePayload = (ObjectNode) operation.payload().deepCopy();
                updatePayload.remove("sessionId");
                SyncOperationRequest updateOperation = new SyncOperationRequest(
                        operation.operationId(), operation.schemaVersion(), operation.entityType(),
                        operation.batchId(), operation.occurredAt(), updatePayload);
                CreateSelectionSessionRequest payload = readPayload(updateOperation, CreateSelectionSessionRequest.class, "selection session update");
                validatePayload(payload, "selection session update");
                if (!operation.operationId().equals(payload.operationId())) {
                    throw new BadRequestException("The operation ID does not match the selection session update payload");
                }
                var execution = selectionCommands.update(operation.batchId(), farmId, userId, sessionId, payload);
                SelectionSessionResponse saved = execution.outcome();
                yield new SyncOperationResult(operation.operationId(), execution.replayed() ? "ALREADY_APPLIED" : "APPLIED",
                        saved.id(), saved.updatedAt(), null);
            }
            case "SEX_COMPOSITION" -> {
                CreateSexCompositionRequest payload = readPayload(operation, CreateSexCompositionRequest.class, "sex composition");
                validatePayload(payload, "sex composition");
                if (!operation.operationId().equals(payload.operationId())) throw new BadRequestException("The operation ID does not match the sex composition payload");
                yield remainingCommands.composition(operation.batchId(),farmId,userId,payload).acknowledgement();
            }
            case "VACCINATION_PLAN" -> {
                long planId=resourceId(operation,"planId");
                RecordVaccinationRequest payload=readPayload(operation,RecordVaccinationRequest.class,"vaccination record");
                if(!operation.operationId().equals(payload.operationId())) throw new BadRequestException("The operation ID does not match the vaccination payload");
                validatePayload(payload,"vaccination record");
                yield remainingCommands.vaccination(planId,farmId,userId,role,payload,operation.batchId(),operation.occurredAt()).acknowledgement();
            }
            case "VACCINATION_SCHEDULE" -> {
                requireManager(role);
                yield applyVaccinationSchedule(operation, farmId, userId);
            }
            case "TASK_STATUS" -> {
                long taskId = resourceId(operation,"taskId");
                var taskPayload = operation.payload().deepCopy();
                if (taskPayload instanceof com.fasterxml.jackson.databind.node.ObjectNode object) object.remove("taskId");
                UpdateTaskStatusRequest payload;
                try {
                    payload = objectMapper.convertValue(taskPayload, UpdateTaskStatusRequest.class);
                } catch (IllegalArgumentException ex) {
                    throw new BadRequestException("Invalid task status payload");
                }
                if (payload.operationId() != null && !operation.operationId().equals(payload.operationId())) {
                    throw new BadRequestException("The operation ID does not match the task status payload");
                }
                if (payload.operationId() == null) {
                    payload = new UpdateTaskStatusRequest(payload.status(), payload.note(), operation.operationId(),
                            payload.occurredAt() == null ? operation.occurredAt() : payload.occurredAt());
                }
                validatePayload(payload, "task status");
                yield remainingCommands.task(taskId,farmId,userId,role==Role.MANAGER,payload,operation.batchId()).acknowledgement();
            }
            default -> throw new BadRequestException("Unsupported offline operation: " + operation.entityType());
        };
    }

    private SyncOperationResult applyVaccinationSchedule(SyncOperationRequest operation, Long farmId, Long userId) {
        if (!(operation.payload() instanceof ObjectNode original)) throw new BadRequestException("Invalid vaccination schedule payload");
        if (original.hasNonNull("operationId") && !operation.operationId().toString().equals(original.path("operationId").asText()))
            throw new BadRequestException("The operation ID does not match the schedule payload");
        ObjectNode canonical = original.deepCopy();
        canonical.put("operationId", operation.operationId().toString());
        canonical.put("commandOccurredAt", operation.occurredAt().toString());
        canonical.put("action", original.path("action").asText("ASSIGN").toUpperCase(java.util.Locale.ROOT));
        var execution = commandReceipts.executeOnce(new CommandReceiptService.CommandIdentity(operation.operationId(),
                operation.schemaVersion(), farmId, userId, "VACCINATION_SCHEDULE", "BATCH", operation.batchId()),
                canonical, SyncOperationResult.class, () -> {
            // Old receipts prove acceptance but do not bind actor/resource/payload.
            // Do not infer an original command from today's mutable plan.
            if (receiptRepository.findById(operation.operationId()).isPresent()) throw new ConflictException(
                    "This legacy schedule receipt cannot verify the original details. Keep the command for manager review.");
            String action = canonical.path("action").asText();
            long programId;
            if (action.equals("CREATE_AND_ASSIGN")) {
                if (original.has("replace") && !original.path("replace").isBoolean())
                    throw new BadRequestException("Schedule replacement must be true or false");
                CreateVaccinationProgramRequest program = objectMapper.convertValue(original.path("program"), CreateVaccinationProgramRequest.class);
                validatePayload(program, "vaccination program");
                programId = vaccinationService.createProgram(farmId, userId, program).id();
                action = original.path("replace").asBoolean(false) ? "REPLACE" : "ASSIGN";
            } else {
                if ((!action.equals("ASSIGN") && !action.equals("REPLACE")) || !original.path("programId").isIntegralNumber()
                        || !original.path("programId").canConvertToLong() || original.path("programId").asLong() <= 0)
                    throw new BadRequestException("Vaccination schedule sync needs a valid action and program ID");
                programId = original.path("programId").asLong();
            }
            List<VaccinationPlanItemResponse> saved = action.equals("REPLACE")
                    ? vaccinationService.replace(operation.batchId(), farmId, userId,
                        new com.poultryprophet.vaccination.dto.ReplaceVaccinationPlanRequest(programId))
                    : vaccinationService.assign(operation.batchId(), farmId, userId,
                        new com.poultryprophet.vaccination.dto.AssignVaccinationProgramRequest(programId));
            return new SyncOperationResult(operation.operationId(), "APPLIED", saved.isEmpty() ? null : saved.getFirst().id(),
                    clock.instant(), null, programId);
        });
        SyncOperationResult saved = execution.outcome();
        return execution.replayed() ? new SyncOperationResult(operation.operationId(), "ALREADY_APPLIED", saved.serverId(),
                saved.serverTime(), saved.message(), saved.programId()) : saved;
    }

    private long resourceId(SyncOperationRequest operation,String field) {
        var value=operation.payload()==null?null:operation.payload().get(field);
        if(value==null || !value.isIntegralNumber() || !value.canConvertToLong() || value.asLong()<=0)
            throw new BadRequestException("The sync payload needs a positive integer " + field);
        return value.asLong();
    }

    private void requireManager(Role role) {
        if (role != Role.MANAGER) {
            throw new BadRequestException("Only farm managers can sync selection sessions or vaccination schedules");
        }
    }

    private SyncOperationResult dailyCommand(SyncOperationRequest operation,Long farmId,Long userId) {
        CreateRecordRequest payload=validateDailyRecord(operation,farmId,userId);
        ObjectNode canonical=(ObjectNode)operation.payload().deepCopy();
        canonical.put("commandOccurredAt",operation.occurredAt().toString());
        var execution=commandReceipts.executeOnce(new CommandReceiptService.CommandIdentity(operation.operationId(),
                operation.schemaVersion(),farmId,userId,"DAILY_RECORD","BATCH",operation.batchId()),canonical,
                SyncOperationResult.class,()->applyDailyRecord(operation,farmId,userId,payload));
        var saved=execution.outcome();
        return execution.replayed()?new SyncOperationResult(operation.operationId(),"ALREADY_APPLIED",saved.serverId(),
                saved.serverTime(),saved.message()):saved;
    }

    private CreateRecordRequest validateDailyRecord(SyncOperationRequest operation, Long farmId, Long userId) {
        if (!(operation.payload() instanceof ObjectNode original)
                || !operation.operationId().toString().equals(original.path("operationId").asText())) {
            throw new BadRequestException("The operation ID does not match the measurement payload");
        }
        if (!original.path("ownerUserId").isIntegralNumber() || !original.path("ownerUserId").canConvertToLong()
                || !original.path("ownerFarmId").isIntegralNumber() || !original.path("ownerFarmId").canConvertToLong()
                || original.path("ownerUserId").asLong() != userId || original.path("ownerFarmId").asLong() != farmId) {
            throw new ConflictException("This measurement belongs to another user or farm.");
        }
        ObjectNode data = original.deepCopy();
        data.remove("operationId");
        data.remove("ownerUserId");
        data.remove("ownerFarmId");
        CreateRecordRequest payload;
        try {
            payload = objectMapper.treeToValue(data, CreateRecordRequest.class);
        } catch (Exception ex) {
            throw new BadRequestException("Invalid daily measurement payload");
        }
        validatePayload(payload, "daily measurement");
        if (payload.recordDate() == null) throw new BadRequestException("A measurement date is required for offline sync");
        if (payload.temperatureC() == null && payload.feedIntakeG() == null && payload.waterIntakeMl() == null
                && payload.mortalityCount() == null && (payload.behaviorNotes() == null || payload.behaviorNotes().isBlank())) {
            throw new BadRequestException("Enter at least one measurement or note");
        }

        return payload;
    }

    private SyncOperationResult applyDailyRecord(SyncOperationRequest operation,Long farmId,Long userId,CreateRecordRequest payload) {
        ObjectNode original=(ObjectNode)operation.payload();
        if (!Boolean.TRUE.equals(jdbcTemplate.queryForObject("SELECT to_regclass('offline_daily_record_receipt') IS NOT NULL",Boolean.class))) {
            // V27 is legacy evidence, not a prerequisite for the shared boundary.
            // The shared UUID reservation and the domain date uniqueness still
            // commit or roll back together when this legacy table never existed.
            var saved=dailyRecordService.recordOffline(operation.batchId(),farmId,payload,userId);
            return SyncOperationResult.applied(operation.operationId(),saved.id());
        }
        // PostgreSQL waits for a concurrent owner of this key before deciding whether
        // the insert succeeded. The receipt and record commit or roll back together.
        String json = original.toString();
        java.sql.Timestamp occurred = java.sql.Timestamp.from(operation.occurredAt());
        int inserted = jdbcTemplate.update("INSERT INTO offline_daily_record_receipt "
                        + "(operation_id, farm_id, user_id, batch_id, occurred_at, payload) VALUES (?, ?, ?, ?, ?, CAST(? AS jsonb)) "
                        + "ON CONFLICT (operation_id) DO NOTHING",
                operation.operationId(), farmId, userId, operation.batchId(), occurred, json);
        if (inserted == 0) {
            List<SyncOperationResult> matching = jdbcTemplate.query("SELECT server_id, created_at FROM offline_daily_record_receipt "
                            + "WHERE operation_id = ? AND farm_id = ? AND user_id = ? AND batch_id = ? "
                            + "AND occurred_at = ? AND payload = CAST(? AS jsonb) AND server_id IS NOT NULL",
                    (rs, row) -> new SyncOperationResult(operation.operationId(),"ALREADY_APPLIED",rs.getLong(1),
                            rs.getTimestamp(2).toInstant(),null), operation.operationId(), farmId, userId, operation.batchId(), occurred, json);
            if (matching.isEmpty()) throw new ConflictException("This operation ID was already used for a different measurement.");
            return matching.getFirst();
        }
        var saved = dailyRecordService.recordOffline(operation.batchId(), farmId, payload, userId);
        jdbcTemplate.update("UPDATE offline_daily_record_receipt SET server_id = ? WHERE operation_id = ?", saved.id(), operation.operationId());
        return SyncOperationResult.applied(operation.operationId(), saved.id());
    }

    private <T> T readPayload(SyncOperationRequest operation, Class<T> payloadType, String label) {
        try {
            T payload = objectMapper.convertValue(operation.payload(), payloadType);
            if (payload == null) {
                throw new BadRequestException("Invalid " + label + " payload");
            }
            return payload;
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException("Invalid " + label + " payload");
        }
    }

    private <T> void validatePayload(T payload, String label) {
        if (payload == null) throw new BadRequestException("Invalid " + label + " payload");
        Set<ConstraintViolation<T>> violations = validator.validate(payload);
        if (!violations.isEmpty()) {
            throw new BadRequestException("Invalid " + label + " payload");
        }
    }
}
