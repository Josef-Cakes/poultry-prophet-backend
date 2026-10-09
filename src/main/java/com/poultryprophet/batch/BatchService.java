package com.poultryprophet.batch;

import com.poultryprophet.batch.dto.BatchResponse;
import com.poultryprophet.batch.dto.BatchTrackingResponse;
import com.poultryprophet.batch.dto.CreateBatchRequest;
import com.poultryprophet.batch.dto.ConfirmHatchDateRequest;
import com.poultryprophet.batch.dto.StageTrackerItem;
import com.poultryprophet.common.BadRequestException;
import com.poultryprophet.common.ConflictException;
import com.poultryprophet.common.NotFoundException;
import com.poultryprophet.event.BatchEvent;
import com.poultryprophet.event.BatchEventRepository;
import com.poultryprophet.population.PopulationProjection;
import com.poultryprophet.population.PopulationProjectionService;
import com.poultryprophet.user.Role;
import com.poultryprophet.user.User;
import com.poultryprophet.user.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * SDD 1.3: business rules for batch registration — name uniqueness per farm, stage
 * validation, and atomic creation of handler assignments.
 */
@Service
public class BatchService {

    private final BatchRepository batchRepository;
    private final LifecycleStageRepository stageRepository;
    private final BatchHandlerAssignmentRepository assignmentRepository;
    private final UserRepository userRepository;
    private final BatchEventRepository eventRepository;
    private final PopulationProjectionService populationProjectionService;
    private final java.time.ZoneId farmZone;

    @Autowired
    public BatchService(BatchRepository batchRepository,
                        LifecycleStageRepository stageRepository,
                        BatchHandlerAssignmentRepository assignmentRepository,
                        UserRepository userRepository,
                        BatchEventRepository eventRepository,
                        PopulationProjectionService populationProjectionService,
                        @Value("${app.time-zone:Asia/Manila}") String timeZone) {
        this.batchRepository = batchRepository;
        this.stageRepository = stageRepository;
        this.assignmentRepository = assignmentRepository;
        this.userRepository = userRepository;
        this.eventRepository = eventRepository;
        this.populationProjectionService = populationProjectionService;
        this.farmZone = java.time.ZoneId.of(timeZone == null ? "Asia/Manila" : timeZone);
    }

    /** Compatibility constructor for focused unit tests that do not load population events. */
    public BatchService(BatchRepository batchRepository,
                        LifecycleStageRepository stageRepository,
                        BatchHandlerAssignmentRepository assignmentRepository,
                        UserRepository userRepository) {
        this(batchRepository, stageRepository, assignmentRepository, userRepository, null,
                new PopulationProjectionService(), "Asia/Manila");
    }

    @Transactional
    public BatchResponse create(CreateBatchRequest request, Long farmId) {
        if (farmId == null) {
            throw new BadRequestException("Join a farm before creating a batch");
        }
        if (batchRepository.existsByFarmIdAndNameIgnoreCase(farmId, request.name())) {
            throw new BadRequestException("A batch named '" + request.name() + "' already exists on this farm");
        }

        Batch batch = new Batch();
        batch.setFarmId(farmId);
        batch.setName(request.name());
        batch.setInitialPopulation(request.initialPopulation());
        batch.setCurrentPopulation(request.initialPopulation());
        batch.setStartDate(request.startDate());
        batch.setBloodline(request.bloodline());
        batch.setSource(request.source());
        // Stage is auto-derived from age (the start/hatch date drives it); a manager can pin a
        // manual override later. This also handles registering older birds via a past start date.
        batch.setStage(autoStageFor(request.startDate()));
        batch.setHatchDateConfirmedAt(java.time.Instant.now());
        batch.setStageManual(false);
        batch.setStatus(BatchStatus.ACTIVE);
        batchRepository.save(batch);

        List<Long> handlerIds = request.handlerUserIds() == null ? List.of() : request.handlerUserIds();
        for (Long handlerId : handlerIds) {
            User handler = userRepository.findById(handlerId)
                    .orElseThrow(() -> new BadRequestException("Unknown handler id " + handlerId));
            if (handler.getRole() != Role.HANDLER) {
                throw new BadRequestException("User " + handlerId + " is not a handler");
            }
            if (!farmId.equals(handler.getFarmId())) {
                throw new BadRequestException("Handler " + handlerId + " belongs to a different farm");
            }
            assignmentRepository.save(new BatchHandlerAssignment(batch, handler));
        }

        return responseFor(batch);
    }

    @Transactional(readOnly = true)
    public List<BatchResponse> listForFarm(Long farmId, boolean archived) {
        List<Batch> batches = archived
                ? batchRepository.findByFarmIdAndStatusOrderByCreatedAtDesc(farmId, BatchStatus.ARCHIVED)
                : batchRepository.findByFarmIdAndStatusNotOrderByCreatedAtDesc(farmId, BatchStatus.ARCHIVED);
        List<BatchResponse> result = new ArrayList<>();
        for (Batch batch : batches) {
            result.add(toResponse(batch));
        }
        return result;
    }

    @Transactional(readOnly = true)
    public BatchResponse getForFarm(Long batchId, Long farmId) {
        return responseFor(requireBatch(batchId, farmId));
    }

    @Transactional
    public BatchResponse confirmHatchDate(Long batchId, Long farmId, Long userId, ConfirmHatchDateRequest request) {
        Batch batch = requireWritableBatch(batchId, farmId);
        if (request.hatchDate().isAfter(LocalDate.now(farmZone))) throw new BadRequestException("Hatch date cannot be in the future");
        batch.setStartDate(request.hatchDate());
        batch.setHatchDateConfirmedAt(java.time.Instant.now());
        batch.setHatchDateConfirmedByUserId(userId);
        batch.setStage(autoStageFor(request.hatchDate()));
        return responseFor(batchRepository.save(batch));
    }

    @Transactional(readOnly = true)
    public BatchTrackingResponse getTracking(Long batchId, Long farmId) {
        Batch batch = requireBatch(batchId, farmId);
        long daysElapsed = BatchAgePolicy.ageDays(batch.getStartDate(), LocalDate.now(farmZone));
        DevelopmentStage current = DevelopmentStage.fromDaysElapsed(daysElapsed);

        List<DevelopmentStage> tracked = Arrays.asList(
                DevelopmentStage.BROODING, DevelopmentStage.RANGING, DevelopmentStage.SELECTION);

        List<StageTrackerItem> tracker = tracked.stream().map(stage -> {
            String status;
            if (stage.isBefore(current)) status = "COMPLETED";
            else if (stage == current) status = "ACTIVE";
            else status = "UPCOMING";
            return new StageTrackerItem(
                    stage, stage.getDisplayName(), stage.getStartDay(), stage.getEndDay(), status);
        }).toList();

        return new BatchTrackingResponse(
                batch.getId(), batch.getName(), batch.getStartDate(),
                daysElapsed, current, current.getDisplayName(), tracker);
    }

    /** Shared accessor used by record/analytics/report flows to enforce farm scoping. */
    @Transactional(readOnly = true)
    public Batch requireBatch(Long batchId, Long farmId) {
        return batchRepository.findByIdAndFarmId(batchId, farmId)
                .orElseThrow(() -> new NotFoundException("Batch " + batchId + " not found"));
    }

    /** Farm-scoped accessor for any endpoint that creates or changes a batch record. */
    @Transactional(readOnly = true)
    public Batch requireWritableBatch(Long batchId, Long farmId) {
        Batch batch = requireBatch(batchId, farmId);
        ensureWritable(batch);
        return batch;
    }

    /** Applies the same guard when a caller already loaded the batch for a read/write workflow. */
    public void ensureWritable(Batch batch) {
        if (batch != null && batch.getStatus() == BatchStatus.ARCHIVED) {
            throw new ConflictException("BATCH_ARCHIVED: this batch is archived. Restore it before adding records.");
        }
    }

    /** The effective stage to display. Lifecycle stage is always derived from batch age. */
    public record StageView(LifecycleStage stage, boolean auto) {
    }

    /**
     * Resolves the stage shown for a batch from its current age. This is deliberately the only
     * source of truth so a manager or handler cannot put a batch in an incorrect stage manually.
     * Shared with the dashboard overview.
     */
    public StageView resolveStage(Batch batch) {
        long days = daysElapsed(batch.getStartDate());
        LifecycleStage auto = stageRepository.findByNameIgnoreCase(autoStageName(days))
                .orElse(batch.getStage()); // fall back to the stored stage if the seed is missing
        return new StageView(auto, true);
    }

    /** Resolves the lifecycle stage as it was on a historical report date. */
    public StageView resolveStage(Batch batch, LocalDate asOfDate) {
        LocalDate effectiveDate = asOfDate == null ? LocalDate.now(farmZone) : asOfDate;
        long days = BatchAgePolicy.ageDays(batch.getStartDate(), effectiveDate);
        LifecycleStage auto = stageRepository.findByNameIgnoreCase(autoStageName(days))
                .orElse(batch.getStage());
        return new StageView(auto, true);
    }

    private BatchResponse toResponse(Batch batch) {
        return responseFor(batch);
    }

    /** Builds a farm-scoped batch response with a reconciled, non-negative display count. */
    public BatchResponse responseFor(Batch batch) {
        StageView stageView = resolveStage(batch);
        PopulationDisplay population = populationDisplay(batch);
        return BatchResponse.from(batch,
                assignmentRepository.findHandlerUserIdsByBatchId(batch.getId()),
                stageView.stage(), stageView.auto(), population.displayPopulation(),
                population.status(), population.warning());
    }

    private PopulationDisplay populationDisplay(Batch batch) {
        if (eventRepository == null) {
            return new PopulationDisplay(Math.max(0, Math.min(batch.getInitialPopulation(), batch.getCurrentPopulation())),
                    batch.getCurrentPopulation() < 0 || batch.getCurrentPopulation() > batch.getInitialPopulation()
                            ? PopulationProjection.RECONCILIATION_REQUIRED : PopulationProjection.VALID,
                    batch.getCurrentPopulation() < 0 || batch.getCurrentPopulation() > batch.getInitialPopulation()
                            ? "The stored current count needs review." : null);
        }
        LocalDate today = LocalDate.now(farmZone);
        List<BatchEvent> events = eventRepository.findByBatchIdAndEventDateBetweenOrderByEventDateAscCreatedAtAsc(
                batch.getId(), batch.getStartDate(), today);
        PopulationProjection projection = populationProjectionService.project(batch, events, today, farmZone);
        int display = projection.validPopulation() == null ? projection.boundedPopulation() : projection.validPopulation();
        return new PopulationDisplay(display, projection.status(), projection.reconciliationMessage());
    }

    private record PopulationDisplay(int displayPopulation, String status, String warning) {}

    /** The lifecycle stage a batch starting on the given date should be in today, by age. */
    private LifecycleStage autoStageFor(LocalDate startDate) {
        String name = autoStageName(daysElapsed(startDate));
        return stageRepository.findByNameIgnoreCase(name)
                .orElseThrow(() -> new BadRequestException("Lifecycle stage '" + name + "' is not configured"));
    }

    private long daysElapsed(LocalDate startDate) {
        return BatchAgePolicy.ageDays(startDate, LocalDate.now(farmZone));
    }

    /**
     * Age band -> lifecycle stage name. Hatch day is day 0; brooding is days 0-30,
     * ranging is days 31-120, and pre-conditioning begins at day 121.
     * Provisional bands per the SDD preface — adjustable here without touching callers.
     */
    private static String autoStageName(long days) {
        return BatchAgePolicy.stageName(days);
    }

    /** Loads a farm-scoped batch while holding a database row lock for accounting writes. */
    @Transactional
    public Batch requireBatchForUpdate(Long batchId, Long farmId) {
        Batch batch = batchRepository.findByIdAndFarmIdForUpdate(batchId, farmId)
                .orElseThrow(() -> new NotFoundException("Batch " + batchId + " not found"));
        return batch;
    }

    /** Row lock plus the archive write guard for event/accounting mutations. */
    @Transactional
    public Batch requireWritableBatchForUpdate(Long batchId, Long farmId) {
        Batch batch = requireBatchForUpdate(batchId, farmId);
        if (batch.getStatus() == BatchStatus.ARCHIVED) {
            throw new ConflictException("BATCH_ARCHIVED: this batch is archived. Restore it before adding records.");
        }
        return batch;
    }
}
