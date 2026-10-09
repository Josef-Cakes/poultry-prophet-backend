package com.poultryprophet.sync;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.poultryprophet.common.BadRequestException;
import com.poultryprophet.common.ConflictException;
import com.poultryprophet.common.NotFoundException;
import com.poultryprophet.event.BatchEventService;
import com.poultryprophet.event.dto.BatchEventResponse;
import com.poultryprophet.event.dto.CreateBatchEventRequest;
import com.poultryprophet.input.FarmInputService;
import com.poultryprophet.input.dto.CreateFarmInputRequest;
import com.poultryprophet.input.dto.FarmInputLogResponse;
import com.poultryprophet.sync.dto.SyncOperationRequest;
import com.poultryprophet.sync.dto.SyncOperationResult;
import com.poultryprophet.sync.dto.SyncOperationsRequest;
import com.poultryprophet.sync.dto.SyncOperationsResponse;
import com.poultryprophet.user.Role;
import com.poultryprophet.sexcomposition.BatchSexCompositionService;
import com.poultryprophet.sexcomposition.dto.CreateSexCompositionRequest;
import com.poultryprophet.sexcomposition.dto.SexCompositionResponse;
import com.poultryprophet.vaccination.VaccinationService;
import com.poultryprophet.vaccination.dto.RecordVaccinationRequest;
import com.poultryprophet.vaccination.dto.VaccinationPlanItemResponse;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/** Versioned replay boundary for the browser outbox. */
@Service
public class SyncOperationService {
    private static final int MAX_OPERATIONS = 25;

    private final ObjectMapper objectMapper;
    private final BatchEventService eventService;
    private final FarmInputService inputService;
    private final BatchSexCompositionService sexCompositionService;
    private final VaccinationService vaccinationService;

    public SyncOperationService(ObjectMapper objectMapper, BatchEventService eventService, FarmInputService inputService,
                                BatchSexCompositionService sexCompositionService, VaccinationService vaccinationService) {
        this.objectMapper = objectMapper; this.eventService = eventService; this.inputService = inputService;
        this.sexCompositionService = sexCompositionService; this.vaccinationService = vaccinationService;
    }

    public SyncOperationsResponse sync(SyncOperationsRequest request, Long farmId, Long userId, Role role) {
        if (request.operations().size() > MAX_OPERATIONS) {
            throw new BadRequestException("Send at most " + MAX_OPERATIONS + " operations at a time");
        }
        List<SyncOperationResult> results = new ArrayList<>();
        int applied = 0, conflicts = 0, rejected = 0, retryable = 0;
        for (SyncOperationRequest operation : request.operations()) {
            try {
                results.add(apply(operation, farmId, userId, role));
                applied++;
            } catch (ConflictException ex) {
                results.add(SyncOperationResult.conflict(operation.operationId(), ex.getMessage()));
                conflicts++;
            } catch (BadRequestException | NotFoundException ex) {
                results.add(SyncOperationResult.rejected(operation.operationId(), ex.getMessage()));
                rejected++;
            } catch (RuntimeException ex) {
                results.add(SyncOperationResult.retryable(operation.operationId(),
                        "The server could not finish this record yet."));
                retryable++;
            }
        }
        return new SyncOperationsResponse(request.operations().size(), applied, conflicts, rejected, retryable, results);
    }

    private SyncOperationResult apply(SyncOperationRequest operation, Long farmId, Long userId, Role role) {
        return switch (operation.entityType()) {
            case "BATCH_EVENT" -> {
                CreateBatchEventRequest payload = objectMapper.convertValue(operation.payload(), CreateBatchEventRequest.class);
                if (!operation.operationId().equals(payload.operationId())) {
                    throw new BadRequestException("The operation ID does not match the event payload");
                }
                BatchEventResponse saved = eventService.create(operation.batchId(), farmId, userId, payload);
                yield SyncOperationResult.applied(operation.operationId(), saved.id());
            }
            case "FARM_INPUT" -> {
                CreateFarmInputRequest payload = objectMapper.convertValue(operation.payload(), CreateFarmInputRequest.class);
                if (!operation.operationId().equals(payload.operationId())) {
                    throw new BadRequestException("The operation ID does not match the input payload");
                }
                if (payload.batchId() != null && !operation.batchId().equals(payload.batchId())) {
                    throw new BadRequestException("The operation batch does not match the input payload");
                }
                FarmInputLogResponse saved = inputService.create(farmId, userId, role, payload);
                yield SyncOperationResult.applied(operation.operationId(), saved.id());
            }
            case "SEX_COMPOSITION" -> {
                CreateSexCompositionRequest payload = objectMapper.convertValue(operation.payload(), CreateSexCompositionRequest.class);
                if (!operation.operationId().equals(payload.operationId())) throw new BadRequestException("The operation ID does not match the sex composition payload");
                SexCompositionResponse saved = sexCompositionService.record(operation.batchId(), farmId, userId, payload);
                yield SyncOperationResult.applied(operation.operationId(), saved.id());
            }
            case "VACCINATION_PLAN" -> {
                Long planId = operation.payload().path("planId").asLong(0);
                if (planId <= 0) throw new BadRequestException("Vaccination sync payload is missing planId");
                RecordVaccinationRequest payload = objectMapper.convertValue(operation.payload(), RecordVaccinationRequest.class);
                if (!operation.operationId().equals(payload.operationId())) throw new BadRequestException("The operation ID does not match the vaccination payload");
                VaccinationPlanItemResponse saved = vaccinationService.record(planId, farmId, userId, role, payload);
                yield SyncOperationResult.applied(operation.operationId(), saved.id());
            }
            default -> throw new BadRequestException("Unsupported offline operation: " + operation.entityType());
        };
    }
}
