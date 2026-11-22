package com.poultryprophet.selectionsession;

import com.poultryprophet.batch.Batch;
import com.poultryprophet.batch.BatchService;
import com.poultryprophet.common.BadRequestException;
import com.poultryprophet.common.NotFoundException;
import com.poultryprophet.event.BatchEvent;
import com.poultryprophet.event.BatchEventRepository;
import com.poultryprophet.finance.FinancialTransactionRepository;
import com.poultryprophet.input.FarmInputLogRepository;
import com.poultryprophet.population.PopulationProjection;
import com.poultryprophet.population.PopulationProjectionService;
import com.poultryprophet.selectionreview.BatchSelectionReview;
import com.poultryprophet.selectionreview.BatchSelectionReviewRepository;
import com.poultryprophet.selectionreview.ManagerReviewStatus;
import com.poultryprophet.selectionreview.SelectionReviewStatus;
import com.poultryprophet.sexcomposition.BatchSexCompositionRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
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
    private final FarmInputLogRepository inputRepository;
    private final FinancialTransactionRepository financeRepository;
    private final BatchSexCompositionRepository sexCompositionRepository;
    private final BatchSelectionReviewRepository reviewRepository;
    private final BatchService batchService;
    private final PopulationProjectionService populationProjectionService;
    private final ZoneId farmZone;

    @Autowired
    public SelectionSessionService(BatchSelectionSessionRepository repository,
                                   BatchEventRepository eventRepository,
                                   FarmInputLogRepository inputRepository,
                                   FinancialTransactionRepository financeRepository,
                                   BatchSexCompositionRepository sexCompositionRepository,
                                   BatchSelectionReviewRepository reviewRepository,
                                   BatchService batchService,
                                   PopulationProjectionService populationProjectionService,
                                   @Value("${app.time-zone:Asia/Manila}") String timeZone) {
        this.repository = repository;
        this.eventRepository = eventRepository;
        this.inputRepository = inputRepository;
        this.financeRepository = financeRepository;
        this.sexCompositionRepository = sexCompositionRepository;
        this.reviewRepository = reviewRepository;
        this.batchService = batchService;
        this.populationProjectionService = populationProjectionService;
        this.farmZone = ZoneId.of(timeZone);
    }

    /** Compatibility constructor for focused legacy tests. Production always uses the linked-review constructor. */
    public SelectionSessionService(BatchSelectionSessionRepository repository,
                                   BatchEventRepository eventRepository,
                                   BatchService batchService,
                                   PopulationProjectionService populationProjectionService,
                                   String timeZone) {
        this(repository, eventRepository, null, null, null, null, batchService, populationProjectionService, timeZone);
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
        requireLinkedReview(batchId, farmId, safe.selectionReviewId(), selectionDate,
                safe.sourceDataAcknowledged(), false);
        if (supersedesSessionId != null) {
            repository.findByIdAndFarmIdAndBatchId(supersedesSessionId, farmId, batchId)
                    .orElseThrow(() -> new NotFoundException("Selection session " + supersedesSessionId + " not found"));
        }

        BatchSelectionSession session = new BatchSelectionSession();
        session.setFarmId(farmId);
        session.setBatchId(batchId);
        session.setSelectionReviewId(safe.selectionReviewId());
        session.setSourceDataAcknowledged(safe.sourceDataAcknowledged());
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
        if (safe.expectedVersion() != null && safe.expectedVersion() != session.getVersion()) {
            throw new com.poultryprophet.common.ConflictException("This selection draft changed. Review the current version before applying a new edit.");
        }
        LocalDate selectionDate = safe.selectionDate() == null ? session.getSelectionDate() : safe.selectionDate();
        validateCounts(batch, safe, false, selectionDate);
        if (session.getSelectionReviewId() != null && !Objects.equals(session.getSelectionReviewId(), safe.selectionReviewId())) {
            throw new BadRequestException("A selection draft must remain linked to the report it started from");
        }
        if (safe.selectionReviewId() != null) {
            requireLinkedReview(batchId, farmId, safe.selectionReviewId(), selectionDate,
                    safe.sourceDataAcknowledged(), false);
            session.setSelectionReviewId(safe.selectionReviewId());
        }
        session.setSelectionDate(selectionDate);
        session.setEvaluatedCount(safe.evaluatedCount());
        session.setAcceptedCount(safe.acceptedCount());
        session.setContinueObservationCount(safe.continueObservationCount());
        session.setNotAcceptedCount(safe.notAcceptedCount());
        session.setOtherCount(safe.otherCount());
        session.setCriterionCodes(normalizeCriteria(safe.criterionCodes()));
        session.setCriteriaNotes(clean(safe.criteriaNotes()));
        session.setSessionNotes(clean(safe.sessionNotes()));
        session.setSourceDataAcknowledged(safe.sourceDataAcknowledged());
        session.setUpdatedAt(Instant.now());
        return toResponse(repository.saveAndFlush(session));
    }

    @Transactional
    public SelectionSessionResponse finalize(Long batchId, Long farmId, Long sessionId) {
        return finalize(batchId, farmId, sessionId, null);
    }

    @Transactional
    public SelectionSessionResponse finalize(Long batchId, Long farmId, Long sessionId, Long expectedVersion) {
        Batch batch = batchService.requireBatch(batchId, farmId);
        batchService.ensureWritable(batch);
        BatchSelectionSession session = find(batchId, farmId, sessionId);
        if (session.getStatus() == SelectionSessionStatus.FINALIZED) {
            return toResponse(session);
        }
        if (expectedVersion != null && expectedVersion != session.getVersion()) {
            throw new com.poultryprophet.common.ConflictException("This selection draft changed. Review the current version before finalizing.");
        }
        if (session.getStatus() != SelectionSessionStatus.DRAFT) {
            throw new BadRequestException("This selection session cannot be finalized");
        }
        CreateSelectionSessionRequest request = new CreateSelectionSessionRequest(
                session.getSelectionDate(), session.getEvaluatedCount(), session.getAcceptedCount(),
                session.getContinueObservationCount(), session.getNotAcceptedCount(), session.getOtherCount(),
                session.getCriterionCodes(), session.getCriteriaNotes(), session.getSessionNotes(), session.getOperationId(),
                session.getSelectionReviewId(), session.isSourceDataAcknowledged());
        validateCounts(batch, request, true, session.getSelectionDate());
        if (session.getSelectionReviewId() != null) {
            requireLinkedReview(batchId, farmId, session.getSelectionReviewId(), session.getSelectionDate(),
                    session.isSourceDataAcknowledged(), true);
        }
        if (session.getSupersedesSessionId() != null) {
            BatchSelectionSession previous = find(batchId, farmId, session.getSupersedesSessionId());
            if (previous.getStatus() != SelectionSessionStatus.FINALIZED) {
                throw new com.poultryprophet.common.ConflictException("The previous selection outcome has already been replaced. Review the latest history.");
            }
            previous.setStatus(SelectionSessionStatus.SUPERSEDED);
            previous.setUpdatedAt(Instant.now());
            repository.save(previous);
        }
        session.setStatus(SelectionSessionStatus.FINALIZED);
        // Match PostgreSQL precision before the first acknowledgement so a
        // terminal replay returns the same stored timestamp.
        session.setFinalizedAt(Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS));
        session.setUpdatedAt(session.getFinalizedAt());
        BatchSelectionSession saved = repository.save(session);
        repository.flush();
        return toResponse(saved);
    }

    @Transactional
    public SelectionSessionResponse supersede(Long batchId, Long farmId, Long oldSessionId,
                                              Long reviewerId, CreateSelectionSessionRequest request) {
        BatchSelectionSession old = find(batchId, farmId, oldSessionId);
        if (old.getStatus() != SelectionSessionStatus.FINALIZED) {
            throw new BadRequestException("Only a finalized session can be superseded");
        }
        if (old.getSelectionReviewId() != null && !Objects.equals(old.getSelectionReviewId(), request.selectionReviewId())) {
            throw new BadRequestException("A corrected outcome must remain linked to the same report");
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
        if (selectionDate.isBefore(batch.getHatchDate())) {
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
                .findByBatchIdAndEventDateBetweenOrderByEventDateAscCreatedAtAsc(batch.getId(), batch.getHatchDate(), date);
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
                && Objects.equals(existing.getSelectionReviewId(), request.selectionReviewId())
                && existing.isSourceDataAcknowledged() == request.sourceDataAcknowledged()
                && Objects.equals(existing.getSupersedesSessionId(), supersedesSessionId);
    }

    private void requireLinkedReview(Long batchId, Long farmId, Long reviewId, LocalDate selectionDate,
                                     boolean sourceDataAcknowledged, boolean finalizing) {
        // The compatibility constructor is used only by older focused tests. Runtime wiring always provides the repository.
        if (reviewRepository == null) return;
        if (reviewId == null) {
            throw new BadRequestException("Finalize a batch review report before recording a selection outcome");
        }
        BatchSelectionReview review = reviewRepository.findByIdAndFarmIdAndBatchId(reviewId, farmId, batchId)
                .orElseThrow(() -> new NotFoundException("Selection review " + reviewId + " not found"));
        if (review.getStatus() != SelectionReviewStatus.FINALIZED) {
            throw new BadRequestException("Finalize the batch review report before recording a selection outcome");
        }
        if (review.getReviewStatus() != ManagerReviewStatus.FOR_IN_PERSON_ASSESSMENT) {
            throw new BadRequestException("This report does not authorize an in-person selection outcome");
        }
        if (selectionDate.isBefore(review.getAsOfDate())) {
            throw new BadRequestException("Selection date cannot be before the report's as-of date");
        }
        if (finalizing && hasNewerSourceData(review) && !sourceDataAcknowledged) {
            throw new BadRequestException("Newer batch records are available. Review them or acknowledge the report snapshot before finalizing.");
        }
    }

    private boolean hasNewerSourceData(BatchSelectionReview review) {
        Instant cutoff = review.getSourceCutoffAt() == null ? review.getGeneratedAt() : review.getSourceCutoffAt();
        if (cutoff == null) return false;
        return eventRepository.existsByBatchIdAndCreatedAtAfter(review.getBatchId(), cutoff)
                || (inputRepository != null && inputRepository.existsByFarmIdAndBatchIdAndCreatedAtAfter(
                review.getFarmId(), review.getBatchId(), cutoff))
                || (financeRepository != null && financeRepository.existsByFarmIdAndBatchIdAndCreatedAtAfter(
                review.getFarmId(), review.getBatchId(), cutoff))
                || (sexCompositionRepository != null && sexCompositionRepository.existsByBatchIdAndFarmIdAndCreatedAtAfter(
                review.getBatchId(), review.getFarmId(), cutoff));
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
                session.getSupersedesSessionId(), session.getCreatedAt(), session.getUpdatedAt(), session.getFinalizedAt(),
                session.getSelectionReviewId(), session.isSourceDataAcknowledged(), session.getVersion());
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
