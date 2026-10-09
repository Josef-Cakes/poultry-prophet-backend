package com.poultryprophet.selectionsession;

import com.poultryprophet.batch.Batch;
import com.poultryprophet.batch.BatchService;
import com.poultryprophet.common.BadRequestException;
import com.poultryprophet.common.NotFoundException;
import com.poultryprophet.event.BatchEvent;
import com.poultryprophet.event.BatchEventRepository;
import com.poultryprophet.population.PopulationProjection;
import com.poultryprophet.population.PopulationProjectionService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
public class SelectionSessionService {
    public static final Set<String> ALLOWED_CRITERIA = Set.of(
            "GENERAL_PHYSICAL_CONDITION",
            "BODY_CONFORMATION",
            "LEGS_AND_MOVEMENT",
            "BEHAVIOR_OR_TEMPERAMENT",
            "SIZE_OR_WEIGHT",
            "HEALTH_HISTORY",
            "BLOODLINE_OR_SOURCE",
            "OTHER");

    private final BatchSelectionSessionRepository repository;
    private final BatchEventRepository eventRepository;
    private final BatchService batchService;
    private final PopulationProjectionService populationProjectionService;
    private final ZoneId farmZone;

    public SelectionSessionService(BatchSelectionSessionRepository repository,
                                   BatchEventRepository eventRepository,
                                   BatchService batchService,
                                   PopulationProjectionService populationProjectionService,
                                   @Value("${app.time-zone:Asia/Manila}") String timeZone) {
        this.repository = repository;
        this.eventRepository = eventRepository;
        this.batchService = batchService;
        this.populationProjectionService = populationProjectionService;
        this.farmZone = ZoneId.of(timeZone);
    }

    @Transactional
    public SelectionSessionResponse create(Long batchId, Long farmId, Long reviewerId,
                                           CreateSelectionSessionRequest request,
                                           Long supersedesSessionId) {
        Batch batch = batchService.requireBatch(batchId, farmId);
        batchService.ensureWritable(batch);
        CreateSelectionSessionRequest safe = requireRequest(request);
        UUID operationId = safe.operationId() == null ? UUID.randomUUID() : safe.operationId();
        LocalDate selectionDate = safe.selectionDate() == null ? today() : safe.selectionDate();

        BatchSelectionSession existing = repository.findByOperationId(operationId).orElse(null);
        if (existing != null) {
            if (!farmId.equals(existing.getFarmId()) || !batchId.equals(existing.getBatchId())) {
                throw new BadRequestException("operationId has already been used for another selection session");
            }
            if (!sameOperation(existing, reviewerId, safe, selectionDate, supersedesSessionId)) {
                throw new BadRequestException("operationId has already been used with different selection details");
            }
            return toResponse(existing);
        }

        validateCounts(batch, safe, false, selectionDate);
        if (supersedesSessionId != null) {
            repository.findByIdAndFarmIdAndBatchId(supersedesSessionId, farmId, batchId)
                    .orElseThrow(() -> new NotFoundException("Selection session " + supersedesSessionId + " not found"));
        }

        BatchSelectionSession session = new BatchSelectionSession();
        session.setFarmId(farmId);
        session.setBatchId(batchId);
        session.setReviewerId(reviewerId);
        session.setSelectionDate(selectionDate);
        session.setEvaluatedCount(safe.evaluatedCount());
        session.setAcceptedCount(safe.acceptedCount());
        session.setContinueObservationCount(safe.continueObservationCount());
        session.setNotAcceptedCount(safe.notAcceptedCount());
        session.setOtherCount(safe.otherCount());
        session.setCriterionCodes(normalizeCriteria(safe.criterionCodes()));
        session.setCriteriaNotes(clean(safe.criteriaNotes()));
        session.setSessionNotes(clean(safe.sessionNotes()));
        session.setOperationId(operationId);
        session.setSupersedesSessionId(supersedesSessionId);
        session.setStatus(SelectionSessionStatus.DRAFT);
        session.setUpdatedAt(Instant.now());
        return toResponse(repository.save(session));
    }

    @Transactional(readOnly = true)
    public List<SelectionSessionResponse> list(Long batchId, Long farmId) {
        batchService.requireBatch(batchId, farmId);
        return repository.findByFarmIdAndBatchIdOrderBySelectionDateDescCreatedAtDesc(farmId, batchId)
                .stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public SelectionSessionResponse get(Long batchId, Long farmId, Long sessionId) {
        batchService.requireBatch(batchId, farmId);
        return toResponse(find(batchId, farmId, sessionId));
    }

    @Transactional
    public SelectionSessionResponse updateDraft(Long batchId, Long farmId, Long sessionId,
                                                CreateSelectionSessionRequest request) {
        Batch batch = batchService.requireBatch(batchId, farmId);
        batchService.ensureWritable(batch);
        BatchSelectionSession session = find(batchId, farmId, sessionId);
        if (session.getStatus() != SelectionSessionStatus.DRAFT) {
            throw new BadRequestException("Only a draft selection session can be edited");
        }
        CreateSelectionSessionRequest safe = requireRequest(request);
        LocalDate selectionDate = safe.selectionDate() == null ? session.getSelectionDate() : safe.selectionDate();
        validateCounts(batch, safe, false, selectionDate);
        session.setSelectionDate(selectionDate);
        session.setEvaluatedCount(safe.evaluatedCount());
        session.setAcceptedCount(safe.acceptedCount());
        session.setContinueObservationCount(safe.continueObservationCount());
        session.setNotAcceptedCount(safe.notAcceptedCount());
        session.setOtherCount(safe.otherCount());
        session.setCriterionCodes(normalizeCriteria(safe.criterionCodes()));
        session.setCriteriaNotes(clean(safe.criteriaNotes()));
        session.setSessionNotes(clean(safe.sessionNotes()));
        session.setUpdatedAt(Instant.now());
        return toResponse(repository.save(session));
    }

    @Transactional
    public SelectionSessionResponse finalize(Long batchId, Long farmId, Long sessionId) {
        Batch batch = batchService.requireBatch(batchId, farmId);
        batchService.ensureWritable(batch);
        BatchSelectionSession session = find(batchId, farmId, sessionId);
        if (session.getStatus() == SelectionSessionStatus.FINALIZED) {
            return toResponse(session);
        }
        if (session.getStatus() != SelectionSessionStatus.DRAFT) {
            throw new BadRequestException("This selection session cannot be finalized");
        }
        CreateSelectionSessionRequest request = new CreateSelectionSessionRequest(
                session.getSelectionDate(), session.getEvaluatedCount(), session.getAcceptedCount(),
                session.getContinueObservationCount(), session.getNotAcceptedCount(), session.getOtherCount(),
                session.getCriterionCodes(), session.getCriteriaNotes(), session.getSessionNotes(), session.getOperationId());
        validateCounts(batch, request, true, session.getSelectionDate());
        session.setStatus(SelectionSessionStatus.FINALIZED);
        session.setFinalizedAt(Instant.now());
        session.setUpdatedAt(session.getFinalizedAt());
        return toResponse(repository.save(session));
    }

    @Transactional
    public SelectionSessionResponse supersede(Long batchId, Long farmId, Long oldSessionId,
                                              Long reviewerId, CreateSelectionSessionRequest request) {
        BatchSelectionSession old = find(batchId, farmId, oldSessionId);
        if (old.getStatus() != SelectionSessionStatus.FINALIZED) {
            throw new BadRequestException("Only a finalized session can be superseded");
        }
        return create(batchId, farmId, reviewerId, request, oldSessionId);
    }

    @Transactional(readOnly = true)
    public SelectionSessionResponse latestFinalized(Long batchId, Long farmId, LocalDate asOfDate) {
        return repository.findTopByFarmIdAndBatchIdAndStatusAndSelectionDateLessThanEqualOrderBySelectionDateDescCreatedAtDesc(
                        farmId, batchId, SelectionSessionStatus.FINALIZED, asOfDate)
                .map(this::toResponse).orElse(null);
    }

    @Transactional(readOnly = true)
    public boolean hasChangedAfter(Long batchId, Long farmId, Instant cutoff) {
        return repository.existsByFarmIdAndBatchIdAndUpdatedAtAfter(farmId, batchId, cutoff);
    }

    private BatchSelectionSession find(Long batchId, Long farmId, Long sessionId) {
        return repository.findByIdAndFarmIdAndBatchId(sessionId, farmId, batchId)
                .orElseThrow(() -> new NotFoundException("Selection session " + sessionId + " not found"));
    }

    private CreateSelectionSessionRequest requireRequest(CreateSelectionSessionRequest request) {
        if (request == null) throw new BadRequestException("Selection session details are required");
        return request;
    }

    private void validateCounts(Batch batch, CreateSelectionSessionRequest request, boolean finalizing,
                                LocalDate selectionDate) {
        if (selectionDate.isBefore(batch.getStartDate())) {
            throw new BadRequestException("Selection date cannot be before the batch start date");
        }
        if (selectionDate.isAfter(today())) {
            throw new BadRequestException("Selection date cannot be in the future");
        }
        if (request.evaluatedCount() < 1) {
            throw new BadRequestException("Enter at least one evaluated bird");
        }
        if (request.acceptedCount() < 0 || request.continueObservationCount() < 0
                || request.notAcceptedCount() < 0 || request.otherCount() < 0) {
            throw new BadRequestException("Selection outcome counts cannot be negative");
        }
        long total = (long) request.acceptedCount() + request.continueObservationCount()
                + request.notAcceptedCount() + request.otherCount();
        if (total > request.evaluatedCount()) {
            throw new BadRequestException("Outcome counts cannot exceed evaluated birds");
        }
        if (finalizing && total != request.evaluatedCount()) {
            throw new BadRequestException("Outcome counts must equal evaluated birds before finalizing");
        }
        long populationAtSelection = populationAt(batch, selectionDate);
        if (request.evaluatedCount() > populationAtSelection) {
            throw new BadRequestException("Evaluated birds cannot exceed the recorded population on the selection date ("
                    + populationAtSelection + ")");
        }
        if (request.otherCount() > 0 && clean(request.sessionNotes()) == null) {
            throw new BadRequestException("Add a note when using the Other outcome");
        }
        normalizeCriteria(request.criterionCodes());
    }

    private long populationAt(Batch batch, LocalDate date) {
        List<BatchEvent> events = eventRepository
                .findByBatchIdAndEventDateBetweenOrderByEventDateAscCreatedAtAsc(batch.getId(), batch.getStartDate(), date);
        PopulationProjection projection = populationProjectionService.project(batch, events, date, farmZone);
        if (projection.validPopulation() == null) {
            throw new BadRequestException("Population records need reconciliation before selection can be recorded"
                    + (projection.reconciliationMessage() == null ? "" : ": " + projection.reconciliationMessage()));
        }
        return projection.validPopulation();
    }

    private boolean sameOperation(BatchSelectionSession existing, Long reviewerId,
                                  CreateSelectionSessionRequest request, LocalDate selectionDate,
                                  Long supersedesSessionId) {
        return Objects.equals(existing.getReviewerId(), reviewerId)
                && Objects.equals(existing.getSelectionDate(), selectionDate)
                && existing.getEvaluatedCount() == request.evaluatedCount()
                && existing.getAcceptedCount() == request.acceptedCount()
                && existing.getContinueObservationCount() == request.continueObservationCount()
                && existing.getNotAcceptedCount() == request.notAcceptedCount()
                && existing.getOtherCount() == request.otherCount()
                && Objects.equals(existing.getCriterionCodes(), normalizeCriteria(request.criterionCodes()))
                && Objects.equals(existing.getCriteriaNotes(), clean(request.criteriaNotes()))
                && Objects.equals(existing.getSessionNotes(), clean(request.sessionNotes()))
                && Objects.equals(existing.getSupersedesSessionId(), supersedesSessionId);
    }

    private Set<String> normalizeCriteria(Set<String> values) {
        Set<String> normalized = new LinkedHashSet<>();
        if (values == null) return normalized;
        for (String value : values) {
            if (value == null || value.isBlank()) continue;
            String code = value.trim().toUpperCase();
            if (!ALLOWED_CRITERIA.contains(code)) {
                throw new BadRequestException("Unsupported selection criterion: " + value);
            }
            normalized.add(code);
        }
        return normalized;
    }

    private SelectionSessionResponse toResponse(BatchSelectionSession session) {
        Double rate = session.getEvaluatedCount() == 0 ? null
                : Math.round(session.getAcceptedCount() * 10000.0 / session.getEvaluatedCount()) / 100.0;
        return new SelectionSessionResponse(session.getId(), session.getFarmId(), session.getBatchId(),
                session.getSelectionDate(), session.getReviewerId(), session.getEvaluatedCount(),
                session.getAcceptedCount(), session.getContinueObservationCount(), session.getNotAcceptedCount(),
                session.getOtherCount(), rate, session.getAcceptedCount(), session.getEvaluatedCount(),
                session.getStatus(), Set.copyOf(session.getCriterionCodes()), session.getCriteriaNotes(),
                session.getSessionNotes(), session.getOperationId() == null ? null : session.getOperationId().toString(),
                session.getSupersedesSessionId(), session.getCreatedAt(), session.getUpdatedAt(), session.getFinalizedAt());
    }

    private LocalDate today() {
        return LocalDate.now(farmZone);
    }

    private String clean(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isBlank() ? null : trimmed;
    }
}
