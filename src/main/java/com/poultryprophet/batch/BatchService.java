package com.poultryprophet.batch;

import com.poultryprophet.batch.dto.BatchResponse;
import com.poultryprophet.batch.dto.BatchTrackingResponse;
import com.poultryprophet.batch.dto.CreateBatchRequest;
import com.poultryprophet.batch.dto.StageTrackerItem;
import com.poultryprophet.common.BadRequestException;
import com.poultryprophet.common.NotFoundException;
import com.poultryprophet.user.Role;
import com.poultryprophet.user.User;
import com.poultryprophet.user.UserRepository;
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

    public BatchService(BatchRepository batchRepository,
                        LifecycleStageRepository stageRepository,
                        BatchHandlerAssignmentRepository assignmentRepository,
                        UserRepository userRepository) {
        this.batchRepository = batchRepository;
        this.stageRepository = stageRepository;
        this.assignmentRepository = assignmentRepository;
        this.userRepository = userRepository;
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

        StageView stageView = resolveStage(batch);
        return BatchResponse.from(batch, new ArrayList<>(handlerIds), stageView.stage(), stageView.auto());
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
        return toResponse(requireBatch(batchId, farmId));
    }

    /** Retires a batch — hides it from the working dashboard list. Reversible via restore. */
    @Transactional
    public BatchResponse archive(Long batchId, Long farmId) {
        Batch batch = requireBatch(batchId, farmId);
        if (batch.getStatus() == BatchStatus.ARCHIVED) {
            throw new BadRequestException("Batch " + batchId + " is already archived");
        }
        batch.setStatus(BatchStatus.ARCHIVED);
        batchRepository.save(batch);
        return toResponse(batch);
    }

    /** Brings an archived batch back to the working list. */
    @Transactional
    public BatchResponse restore(Long batchId, Long farmId) {
        Batch batch = requireBatch(batchId, farmId);
        if (batch.getStatus() != BatchStatus.ARCHIVED) {
            throw new BadRequestException("Batch " + batchId + " is not archived");
        }
        batch.setStatus(BatchStatus.ACTIVE);
        batchRepository.save(batch);
        return toResponse(batch);
    }

    @Transactional(readOnly = true)
    public BatchTrackingResponse getTracking(Long batchId, Long farmId) {
        Batch batch = requireBatch(batchId, farmId);
        long daysElapsed = Math.max(1, ChronoUnit.DAYS.between(batch.getStartDate(), LocalDate.now()));
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

    private BatchResponse toResponse(Batch batch) {
        StageView stageView = resolveStage(batch);
        return BatchResponse.from(batch,
                assignmentRepository.findHandlerUserIdsByBatchId(batch.getId()),
                stageView.stage(), stageView.auto());
    }

    /** The lifecycle stage a batch starting on the given date should be in today, by age. */
    private LifecycleStage autoStageFor(LocalDate startDate) {
        String name = autoStageName(daysElapsed(startDate));
        return stageRepository.findByNameIgnoreCase(name)
                .orElseThrow(() -> new BadRequestException("Lifecycle stage '" + name + "' is not configured"));
    }

    private static long daysElapsed(LocalDate startDate) {
        return Math.max(1, ChronoUnit.DAYS.between(startDate, LocalDate.now()));
    }

    /**
     * Age band -> lifecycle stage name. Brooding day 1-30 (≈ first month, heat-dependent chick
     * stage), ranging 31-120 (grow-out), pre-conditioning 121+ (month-5 selection onward).
     * Provisional bands per the SDD preface — adjustable here without touching callers.
     */
    private static String autoStageName(long days) {
        if (days <= 30) return "brooding";
        if (days <= 120) return "ranging";
        return "pre-conditioning";
    }

    /** Loads a farm-scoped batch while holding a database row lock for accounting writes. */
    @Transactional
    public Batch requireBatchForUpdate(Long batchId, Long farmId) {
        return batchRepository.findByIdAndFarmIdForUpdate(batchId, farmId)
                .orElseThrow(() -> new NotFoundException("Batch " + batchId + " not found"));
    }
}
