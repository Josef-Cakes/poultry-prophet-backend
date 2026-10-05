package com.poultryprophet.selectionreview;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfWriter;
import com.poultryprophet.batch.Batch;
import com.poultryprophet.batch.BatchService;
import com.poultryprophet.common.BadRequestException;
import com.poultryprophet.common.NotFoundException;
import com.poultryprophet.event.BatchEvent;
import com.poultryprophet.event.BatchEventRepository;
import com.poultryprophet.event.EventType;
import com.poultryprophet.finance.FinanceTransactionStatus;
import com.poultryprophet.finance.FinanceTransactionType;
import com.poultryprophet.finance.FinancialTransaction;
import com.poultryprophet.finance.FinancialTransactionRepository;
import com.poultryprophet.incubation.IncubationCycle;
import com.poultryprophet.incubation.IncubationCycleRepository;
import com.poultryprophet.input.FarmInputLog;
import com.poultryprophet.input.FarmInputLogRepository;
import com.poultryprophet.selectionsession.SelectionSessionResponse;
import com.poultryprophet.selectionsession.SelectionSessionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds a factual, batch-level review from existing farm records. It intentionally does not
 * depend on the legacy AnalyticsService, thresholds, BirdScore, or CRS ranking logic.
 */
@Service
public class SelectionReviewService {
    public static final String PAYLOAD_VERSION = "selection-review-v3";

    private final BatchSelectionReviewRepository reviewRepository;
    private final BatchEventRepository eventRepository;
    private final FarmInputLogRepository inputRepository;
    private final IncubationCycleRepository incubationRepository;
    private final FinancialTransactionRepository financeRepository;
    private final BatchService batchService;
    private final ObjectMapper objectMapper;
    private final SelectionSessionService selectionSessionService;

    @Value("${app.time-zone:Asia/Manila}")
    private String timeZone;

    @Autowired
    public SelectionReviewService(BatchSelectionReviewRepository reviewRepository,
                                  BatchEventRepository eventRepository,
                                  FarmInputLogRepository inputRepository,
                                  IncubationCycleRepository incubationRepository,
                                  FinancialTransactionRepository financeRepository,
                                  BatchService batchService,
                                  ObjectMapper objectMapper,
                                  SelectionSessionService selectionSessionService) {
        this.reviewRepository = reviewRepository;
        this.eventRepository = eventRepository;
        this.inputRepository = inputRepository;
        this.incubationRepository = incubationRepository;
        this.financeRepository = financeRepository;
        this.batchService = batchService;
        this.objectMapper = objectMapper;
        this.selectionSessionService = selectionSessionService;
    }

    /** Compatibility constructor for focused unit tests that do not load selection sessions. */
    public SelectionReviewService(BatchSelectionReviewRepository reviewRepository,
                                  BatchEventRepository eventRepository,
                                  FarmInputLogRepository inputRepository,
                                  IncubationCycleRepository incubationRepository,
                                  FinancialTransactionRepository financeRepository,
                                  BatchService batchService,
                                  ObjectMapper objectMapper) {
        this(reviewRepository, eventRepository, inputRepository, incubationRepository,
                financeRepository, batchService, objectMapper, null);
    }

    @Transactional(readOnly = true)
    public SelectionReviewPayload preview(Long batchId, Long farmId, LocalDate periodStart,
                                          LocalDate periodEnd, LocalDate asOfDate,
                                          boolean includeFinance) {
        Batch batch = batchService.requireBatch(batchId, farmId);
        Dates dates = normalizeDates(batch, periodStart, periodEnd, asOfDate);
        return aggregate(batch, farmId, dates.periodStart(), dates.periodEnd(), dates.asOfDate(), includeFinance);
    }

    @Transactional
    public SelectionReviewResponse create(Long batchId, Long farmId, Long userId,
                                          CreateSelectionReviewRequest request) {
        Batch batch = batchService.requireBatch(batchId, farmId);
        String idempotencyKey = clean(request == null ? null : request.idempotencyKey());
        if (idempotencyKey != null) {
            var existing = reviewRepository.findByFarmIdAndBatchIdAndIdempotencyKey(farmId, batchId, idempotencyKey);
            if (existing.isPresent()) {
                return toResponse(existing.get(), true);
            }
        }
        Dates dates = normalizeDates(batch,
                request == null ? null : request.periodStart(),
                request == null ? null : request.periodEnd(),
                request == null ? null : request.asOfDate());
        SelectionReviewPayload payload = aggregate(batch, farmId, dates.periodStart(), dates.periodEnd(),
                dates.asOfDate(), true);
        Instant sourceCutoffAt = Instant.now();

        BatchSelectionReview review = new BatchSelectionReview();
        review.setFarmId(farmId);
        review.setBatchId(batchId);
        review.setPeriodStart(dates.periodStart());
        review.setPeriodEnd(dates.periodEnd());
        review.setAsOfDate(dates.asOfDate());
        review.setStatus(SelectionReviewStatus.DRAFT);
        review.setReviewStatus(ManagerReviewStatus.NOT_REVIEWED);
        review.setPayloadVersion(PAYLOAD_VERSION);
        review.setPayloadJson(writeJson(payload));
        review.setVersionNumber((int) reviewRepository.countByFarmIdAndBatchId(farmId, batchId) + 1);
        review.setPurpose(defaultPurpose(request == null ? null : request.purpose()));
        review.setSnapshotNote(clean(request == null ? null : request.snapshotNote()));
        review.setSourceCutoffAt(sourceCutoffAt);
        review.setIdempotencyKey(idempotencyKey);
        review.setGeneratedBy(userId);
        review.setGeneratedAt(sourceCutoffAt);
        return toResponse(reviewRepository.save(review), true);
    }

    @Transactional(readOnly = true)
    public List<SelectionReviewResponse> list(Long batchId, Long farmId, boolean includeFinance) {
        batchService.requireBatch(batchId, farmId);
        return reviewRepository.findByFarmIdAndBatchIdOrderByGeneratedAtDesc(farmId, batchId)
                .stream().map(review -> toResponse(review, includeFinance)).toList();
    }

    @Transactional(readOnly = true)
    public SelectionReviewResponse get(Long batchId, Long farmId, Long reviewId, boolean includeFinance) {
        batchService.requireBatch(batchId, farmId);
        BatchSelectionReview review = reviewRepository.findByIdAndFarmIdAndBatchId(reviewId, farmId, batchId)
                .orElseThrow(() -> new NotFoundException("Selection review " + reviewId + " not found"));
        return toResponse(review, includeFinance);
    }

    @Transactional
    public SelectionReviewResponse finalize(Long batchId, Long farmId, Long reviewId, Long userId,
                                            FinalizeSelectionReviewRequest request) {
        batchService.requireBatch(batchId, farmId);
        BatchSelectionReview review = reviewRepository.findByIdAndFarmIdAndBatchId(reviewId, farmId, batchId)
                .orElseThrow(() -> new NotFoundException("Selection review " + reviewId + " not found"));
        if (review.getStatus() == SelectionReviewStatus.FINALIZED) {
            throw new BadRequestException("A finalized selection review is immutable; create a new review for corrected source data");
        }

        String notes = request.managerNotes() == null ? null : request.managerNotes().trim();
        review.setReviewStatus(request.reviewStatus());
        review.setManagerNotes(notes == null || notes.isBlank() ? null : notes);
        review.setNextReviewDate(request.nextReviewDate());
        review.setStatus(SelectionReviewStatus.FINALIZED);
        review.setReviewedBy(userId);
        review.setReviewedAt(Instant.now());

        SelectionReviewPayload oldPayload = readPayload(review.getPayloadJson());
        SelectionReviewPayload finalPayload = withReviewInstructions(oldPayload, new SelectionReviewPayload.ReviewInstructions(
                request.reviewStatus().name(), review.getManagerNotes(), review.getNextReviewDate(),
                String.valueOf(userId), review.getReviewedAt()));
        review.setPayloadJson(writeJson(finalPayload));
        return toResponse(reviewRepository.save(review), true);
    }

    @Transactional(readOnly = true)
    public byte[] exportPdf(Long batchId, Long farmId, Long reviewId) {
        SelectionReviewResponse response = get(batchId, farmId, reviewId, true);
        return renderPdf(response);
    }

    private SelectionReviewPayload aggregate(Batch batch, Long farmId, LocalDate periodStart,
                                             LocalDate periodEnd, LocalDate asOfDate,
                                             boolean includeFinance) {
        List<BatchEvent> events = eventRepository
                .findByBatchIdAndEventDateBetweenOrderByEventDateAscCreatedAtAsc(batch.getId(), periodStart, periodEnd);
        List<BatchEvent> eventsThroughAsOf = periodStart.equals(batch.getStartDate())
                && periodEnd.equals(asOfDate)
                ? events
                : eventRepository.findByBatchIdAndEventDateBetweenOrderByEventDateAscCreatedAtAsc(
                        batch.getId(), batch.getStartDate(), asOfDate);
        List<FarmInputLog> products = inputRepository.findByFarmIdAndBatchIdOrderByRecordedAtDesc(farmId, batch.getId())
                .stream().filter(value -> within(value.getRecordedAt(), periodStart, periodEnd)).toList();

        // Keep the shared service accessor here so existing report tests and callers retain the
        // same stage fallback behavior. The report's age is still calculated from its as-of date.
        BatchService.StageView stage = batchService.resolveStage(batch);
        LocalDate latestEventDate = events.stream().map(BatchEvent::getEventDate).max(LocalDate::compareTo).orElse(null);
        SelectionReviewPayload.PopulationSummary population = populationSummary(batch, eventsThroughAsOf, asOfDate);

        List<SelectionReviewPayload.HealthEventItem> healthEvents = events.stream()
                .filter(value -> value.getEventType() == EventType.HEALTH_DEATH
                        || value.getEventType() == EventType.HEALTH_CONCERN
                        || value.getEventType() == EventType.BEHAVIOR_OBSERVATION)
                .map(value -> new SelectionReviewPayload.HealthEventItem(
                        value.getId(), value.getEventDate(), value.getEventType().name(), value.getTitle(),
                        value.getSeverityLabel(), value.getAffectedCount(), value.getDetails(), value.getTags(),
                        value.getHandlerId()))
                .toList();

        List<SelectionReviewPayload.ProductUseItem> productUse = products.stream()
                .sorted(Comparator.comparing(FarmInputLog::getRecordedAt))
                .map(value -> new SelectionReviewPayload.ProductUseItem(
                        value.getId(), value.getRecordedAt(), value.getProductType().name(), value.getBrandName(),
                        value.getProductName(), value.getQuantity(), value.getUnit(), value.getPurpose(),
                        value.getNotes(), value.getRecordedBy()))
                .toList();

        SelectionReviewPayload.IncubationSummary incubation = incubationRepository
                .findByFarmIdAndCreatedBatchId(farmId, batch.getId()).stream()
                .max(Comparator.comparing(IncubationCycle::getLoadedDate))
                .map(this::incubationSummary)
                .orElse(null);

        SelectionReviewPayload.FinanceSummary finance = includeFinance
                ? financeSummary(farmId, batch.getId(), periodStart, periodEnd)
                : null;

        List<SelectionReviewPayload.DataAvailabilityItem> availability = new ArrayList<>();
        availability.add(availability("Population events", events.size(), latestEventDate,
                "No recorded event does not prove that no event occurred."));
        if (population.reconciliationRequired()) {
            availability.add(new SelectionReviewPayload.DataAvailabilityItem(
                    "Population reconciliation", "PARTIAL", population.sourceEventCount(), latestEventDate,
                    population.reconciliationMessage()));
        }
        availability.add(availability("Health history", healthEvents.size(),
                healthEvents.stream().map(SelectionReviewPayload.HealthEventItem::eventDate).max(LocalDate::compareTo).orElse(null),
                "Only recorded observations and health-related events are shown; this is not a diagnosis."));
        availability.add(availability("Product-use history", productUse.size(),
                productUse.stream().map(value -> value.recordedAt().atZone(farmZone()).toLocalDate()).max(LocalDate::compareTo).orElse(null),
                "Quantity may be unavailable when the farm records only a pack, sachet, or product name."));
        availability.add(incubation == null
                ? new SelectionReviewPayload.DataAvailabilityItem("Incubation context", "NOT_APPLICABLE", 0, null,
                "This batch is not linked to a recorded incubation cycle.")
                : new SelectionReviewPayload.DataAvailabilityItem("Incubation context", "AVAILABLE", 1,
                incubation.actualHatchDate() != null ? incubation.actualHatchDate() : incubation.loadedDate(),
                incubation.limitation()));
        if (includeFinance) {
            availability.add(new SelectionReviewPayload.DataAvailabilityItem("Batch finance", finance == null ? "NO_RECORDS" : finance.postedTransactionCount() == 0 ? "NO_RECORDS" : "PARTIAL",
                    finance == null ? 0 : (int) finance.postedTransactionCount(),
                    finance == null ? null : finance.endDate(),
                    "Financial totals are recorded cash-flow entries and may be incomplete."));
        }

        SelectionReviewPayload.SelectionSummary selectionSummary = null;
        if (selectionSessionService != null) {
            SelectionSessionResponse latestSelection = selectionSessionService.latestFinalized(batch.getId(), farmId, asOfDate);
            if (latestSelection != null) {
                selectionSummary = new SelectionReviewPayload.SelectionSummary(
                        latestSelection.id(), latestSelection.selectionDate(), latestSelection.status().name(),
                        latestSelection.evaluatedCount(), latestSelection.acceptedCount(),
                        latestSelection.continueObservationCount(), latestSelection.notAcceptedCount(),
                        latestSelection.otherCount(), latestSelection.selectionRatePercent(),
                        latestSelection.criterionCodes(), latestSelection.reviewerId(), latestSelection.criteriaNotes(),
                        latestSelection.sessionNotes());
            }
            availability.add(selectionSummary == null
                    ? new SelectionReviewPayload.DataAvailabilityItem("Selection session", "NO_RECORDS", 0, null,
                    "No finalized batch-level selection session was recorded by the as-of date.")
                    : new SelectionReviewPayload.DataAvailabilityItem("Selection session", "AVAILABLE", 1,
                    selectionSummary.selectionDate(),
                    "Selection outcome was recorded by a manager; it does not change population automatically."));
        }

        SelectionReviewPayload.BatchOverview overview = new SelectionReviewPayload.BatchOverview(
                batch.getId(), batch.getName(), batch.getBloodline(), batch.getSource(), batch.getInitialPopulation(),
                population.currentPopulation(), population.currentPopulation() + " / " + batch.getInitialPopulation(),
                Math.max(1, ChronoUnit.DAYS.between(batch.getStartDate(), asOfDate) + 1), stage.stage().getName(),
                batch.getStartDate(), latestEventDate);

        return new SelectionReviewPayload(
                "Batch Selection Review Report",
                "Descriptive decision support only. This report does not diagnose, predict performance, rank birds, or automatically select a bird.",
                PAYLOAD_VERSION, periodStart, periodEnd, asOfDate, overview, population, healthEvents, productUse,
                incubation, finance, availability,
                selectionSummary,
                new SelectionReviewPayload.ReviewInstructions(ManagerReviewStatus.NOT_REVIEWED.name(), null, null, null, null));
    }

    private SelectionReviewPayload.PopulationSummary populationSummary(Batch batch, List<BatchEvent> events,
                                                                         LocalDate asOfDate) {
        Map<String, Long> categories = new LinkedHashMap<>();
        categories.put("healthRelatedDeaths", 0L);
        categories.put("accidentalDeaths", 0L);
        categories.put("predation", 0L);
        categories.put("missing", 0L);
        categories.put("returned", 0L);
        categories.put("transfersOut", 0L);
        categories.put("transfersIn", 0L);
        categories.put("sales", 0L);
        categories.put("culling", 0L);
        categories.put("countCorrections", 0L);
        categories.put("legacyMortalityRecords", 0L);
        categories.put("salesForBreeding", 0L);
        categories.put("salesOtherPurpose", 0L);

        long calculatedPopulation = batch.getInitialPopulation();
        for (BatchEvent event : events) {
            long delta = populationDelta(event);
            calculatedPopulation += delta;
            long amount = Math.abs(event.getPopulationDelta() == null ? delta : (long) event.getPopulationDelta());
            String key = switch (event.getEventType()) {
                case HEALTH_DEATH -> "healthRelatedDeaths";
                case ACCIDENTAL_DEATH -> "accidentalDeaths";
                case SUSPECTED_PREDATION, CONFIRMED_PREDATION -> "predation";
                case MISSING -> "missing";
                case FOUND_RETURNED -> "returned";
                case TRANSFER_OUT -> "transfersOut";
                case TRANSFER_IN -> "transfersIn";
                case SALE -> "sales";
                case CULLING -> "culling";
                case COUNT_CORRECTION -> "countCorrections";
                case MORTALITY -> "legacyMortalityRecords";
                default -> null;
            };
            if (key != null) categories.computeIfPresent(key, (ignored, value) -> value + amount);
            if (event.getEventType() == EventType.SALE && event.getSalePurpose() != null) {
                String purposeKey = event.getSalePurpose() == com.poultryprophet.event.SalePurpose.BREEDING
                        ? "salesForBreeding" : "salesOtherPurpose";
                categories.computeIfPresent(purposeKey, (ignored, value) -> value + amount);
            }
        }

        boolean currentAsOfDate = asOfDate.equals(LocalDate.now(farmZone()));
        long preferredPopulation = currentAsOfDate ? batch.getCurrentPopulation() : calculatedPopulation;
        int boundedPopulation = (int) Math.max(0L,
                Math.min((long) batch.getInitialPopulation(), preferredPopulation));
        boolean eventTotalOutsideBounds = calculatedPopulation < 0
                || calculatedPopulation > batch.getInitialPopulation();
        boolean currentProjectionOutsideBounds = batch.getCurrentPopulation() < 0
                || batch.getCurrentPopulation() > batch.getInitialPopulation();
        boolean projectionMismatch = currentAsOfDate
                && calculatedPopulation != batch.getCurrentPopulation();
        boolean reconciliationRequired = eventTotalOutsideBounds
                || currentProjectionOutsideBounds
                || projectionMismatch;
        String reconciliationMessage = reconciliationRequired
                ? populationReconciliationMessage(batch, calculatedPopulation, boundedPopulation,
                        currentAsOfDate, projectionMismatch)
                : null;

        long healthDeaths = categories.get("healthRelatedDeaths");
        Double rate = batch.getInitialPopulation() == 0 || healthDeaths > batch.getInitialPopulation() ? null
                : round(healthDeaths * 100.0 / batch.getInitialPopulation(), 2);
        return new SelectionReviewPayload.PopulationSummary(
                batch.getInitialPopulation(), boundedPopulation, calculatedPopulation,
                reconciliationRequired, reconciliationMessage, healthDeaths, rate,
                categories.get("accidentalDeaths"), categories.get("predation"), categories.get("missing"),
                categories.get("returned"), categories.get("transfersOut"), categories.get("transfersIn"),
                categories.get("sales"), categories.get("culling"), categories.get("countCorrections"),
                categories.get("legacyMortalityRecords"), events.size(), categories);
    }

    private String populationReconciliationMessage(Batch batch, long calculatedPopulation,
                                                   int boundedPopulation, boolean currentAsOfDate,
                                                   boolean projectionMismatch) {
        if (currentAsOfDate && projectionMismatch) {
            return "Population events calculate " + calculatedPopulation
                    + " alive, while the batch record contains " + batch.getCurrentPopulation()
                    + ". Showing " + boundedPopulation
                    + "; review duplicate, legacy, or incorrect population events.";
        }
        return "Population events calculate " + calculatedPopulation
                + " alive, outside the valid range of 0 to " + batch.getInitialPopulation()
                + ". Showing " + boundedPopulation
                + "; review duplicate, legacy, or incorrect population events.";
    }

    private SelectionReviewPayload.IncubationSummary incubationSummary(IncubationCycle cycle) {
        Double hatchRate = cycle.getEggsLoaded() == 0 || cycle.getHatchedCount() == null ? null
                : round(cycle.getHatchedCount() * 100.0 / cycle.getEggsLoaded(), 2);
        String limitation = cycle.getHatchedCount() == null
                ? "Hatched count has not been recorded."
                : "Hatch rate is hatched eggs divided by eggs loaded; it is not a fertility diagnosis.";
        return new SelectionReviewPayload.IncubationSummary(
                cycle.getId(), cycle.getCycleName(), cycle.getIncubatorCode(), cycle.getLoadedDate(),
                cycle.getEggsLoaded(), cycle.getExpectedHatchDate(), cycle.getActualHatchDate(), cycle.getHatchedCount(),
                cycle.getUnhatchedCount(), cycle.getRemovedDamagedCount(), hatchRate, limitation);
    }

    private SelectionReviewPayload.FinanceSummary financeSummary(Long farmId, Long batchId,
                                                                  LocalDate start, LocalDate end) {
        List<FinancialTransaction> transactions = financeRepository
                .findByFarmIdAndBatchIdAndTransactionDateBetweenOrderByTransactionDateAsc(farmId, batchId, start, end);
        List<FinancialTransaction> posted = transactions.stream()
                .filter(value -> value.getStatus() == FinanceTransactionStatus.POSTED).toList();
        BigDecimal income = posted.stream().filter(value -> value.getType() == FinanceTransactionType.INCOME)
                .map(FinancialTransaction::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal expense = posted.stream().filter(value -> value.getType() == FinanceTransactionType.EXPENSE)
                .map(FinancialTransaction::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new SelectionReviewPayload.FinanceSummary(start, end, income, expense, income.subtract(expense),
                posted.size(), true, "Recorded net cash flow is not accounting profit and may omit unentered costs or income.");
    }

    private SelectionReviewPayload.DataAvailabilityItem availability(String section, int count,
                                                                      LocalDate latest, String message) {
        return new SelectionReviewPayload.DataAvailabilityItem(section, count == 0 ? "NO_RECORDS" : "AVAILABLE",
                count, latest, message);
    }

    private boolean within(Instant timestamp, LocalDate start, LocalDate end) {
        if (timestamp == null) return false;
        ZoneId zone = farmZone();
        Instant from = start.atStartOfDay(zone).toInstant();
        Instant until = end.plusDays(1).atStartOfDay(zone).toInstant();
        return !timestamp.isBefore(from) && timestamp.isBefore(until);
    }

    private ZoneId farmZone() {
        return ZoneId.of(timeZone == null || timeZone.isBlank() ? "Asia/Manila" : timeZone);
    }

    private Dates normalizeDates(Batch batch, LocalDate requestedStart, LocalDate requestedEnd, LocalDate requestedAsOf) {
        LocalDate today = LocalDate.now(farmZone());
        LocalDate end = requestedEnd == null ? today : requestedEnd;
        LocalDate start = requestedStart == null ? batch.getStartDate() : requestedStart;
        LocalDate asOf = requestedAsOf == null ? end : requestedAsOf;
        if (start.isAfter(end)) throw new BadRequestException("periodStart must not be after periodEnd");
        if (asOf.isBefore(start) || asOf.isAfter(end)) throw new BadRequestException("asOfDate must be within the report period");
        if (end.isAfter(today)) throw new BadRequestException("Report dates cannot be in the future");
        return new Dates(start, end, asOf);
    }

    private SelectionReviewResponse toResponse(BatchSelectionReview review, boolean includeFinance) {
        SelectionReviewPayload payload = readPayload(review.getPayloadJson());
        if (!includeFinance && payload.finance() != null) {
            List<SelectionReviewPayload.DataAvailabilityItem> publicAvailability = payload.dataAvailability().stream()
                    .filter(item -> !"Batch finance".equals(item.section())).toList();
            payload = new SelectionReviewPayload(payload.reportTitle(), payload.disclaimer(), payload.payloadVersion(),
                    payload.periodStart(), payload.periodEnd(), payload.asOfDate(), payload.batch(), payload.population(),
                    payload.healthEvents(), payload.productUse(), payload.incubation(), null, publicAvailability,
                    payload.selectionSummary(),
                    payload.reviewInstructions());
        }
        Instant sourceCutoff = review.getSourceCutoffAt() == null ? review.getGeneratedAt() : review.getSourceCutoffAt();
        boolean newerDataAvailable = sourceCutoff != null && (
                eventRepository.existsByBatchIdAndCreatedAtAfter(review.getBatchId(), sourceCutoff)
                        || inputRepository.existsByFarmIdAndBatchIdAndCreatedAtAfter(review.getFarmId(), review.getBatchId(), sourceCutoff)
                        || financeRepository.existsByFarmIdAndBatchIdAndCreatedAtAfter(review.getFarmId(), review.getBatchId(), sourceCutoff)
                        || (selectionSessionService != null
                        && selectionSessionService.hasChangedAfter(review.getBatchId(), review.getFarmId(), sourceCutoff)));
        return new SelectionReviewResponse(review.getId(), review.getFarmId(), review.getBatchId(),
                payload.batch().batchName(), review.getPeriodStart(), review.getPeriodEnd(), review.getAsOfDate(),
                review.getStatus(), review.getReviewStatus(), review.getManagerNotes(), review.getNextReviewDate(),
                review.getVersionNumber() == null ? 1 : review.getVersionNumber(), review.getPurpose(), review.getSnapshotNote(),
                review.getPayloadVersion(), payload, review.getGeneratedBy(), review.getGeneratedAt(), sourceCutoff,
                newerDataAvailable,
                review.getReviewedBy(), review.getReviewedAt());
    }

    private SelectionReviewPayload withReviewInstructions(SelectionReviewPayload payload,
                                                           SelectionReviewPayload.ReviewInstructions instructions) {
        return new SelectionReviewPayload(payload.reportTitle(), payload.disclaimer(), payload.payloadVersion(),
                payload.periodStart(), payload.periodEnd(), payload.asOfDate(), payload.batch(), payload.population(),
                payload.healthEvents(), payload.productUse(), payload.incubation(), payload.finance(),
                payload.dataAvailability(), payload.selectionSummary(), instructions);
    }

    private String writeJson(SelectionReviewPayload payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not serialize selection review", exception);
        }
    }

    private SelectionReviewPayload readPayload(String json) {
        try {
            return normalizeStoredPopulation(objectMapper.readValue(json, SelectionReviewPayload.class));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not read selection review snapshot", exception);
        }
    }

    /**
     * Older immutable snapshots may contain an impossible negative population. Preserve their
     * recorded event totals for audit, but never present an impossible alive count as valid.
     */
    private SelectionReviewPayload normalizeStoredPopulation(SelectionReviewPayload payload) {
        if (payload == null || payload.population() == null || payload.batch() == null) return payload;
        SelectionReviewPayload.PopulationSummary population = payload.population();
        SelectionReviewPayload.BatchOverview batch = payload.batch();
        boolean currentOutsideBounds = population.currentPopulation() < 0
                || population.currentPopulation() > population.initialPopulation();
        boolean batchOutsideBounds = batch.currentPopulation() < 0
                || batch.currentPopulation() > batch.initialPopulation();
        boolean legacyPayload = !PAYLOAD_VERSION.equals(payload.payloadVersion());
        boolean reconciliationRequired = population.reconciliationRequired()
                || currentOutsideBounds || batchOutsideBounds;
        if (!reconciliationRequired) return payload;

        int boundedPopulation = (int) Math.max(0L,
                Math.min((long) population.initialPopulation(), population.currentPopulation()));
        long calculatedPopulation = legacyPayload
                ? population.currentPopulation()
                : population.calculatedPopulationFromEvents();
        String message = clean(population.reconciliationMessage());
        if (message == null) {
            message = "This saved report contained a calculated population of " + calculatedPopulation
                    + ". Showing " + boundedPopulation
                    + "; review duplicate, legacy, or incorrect population events.";
        }

        SelectionReviewPayload.PopulationSummary safePopulation = new SelectionReviewPayload.PopulationSummary(
                population.initialPopulation(), boundedPopulation, calculatedPopulation, true, message,
                population.healthRelatedDeaths(), population.healthRelatedLossPercentage(),
                population.accidentalDeaths(), population.predation(), population.missing(), population.returned(),
                population.transfersOut(), population.transfersIn(), population.sales(), population.culling(),
                population.countCorrections(), population.legacyMortalityRecords(), population.sourceEventCount(),
                population.categoryCounts());
        SelectionReviewPayload.BatchOverview safeBatch = new SelectionReviewPayload.BatchOverview(
                batch.batchId(), batch.batchName(), batch.bloodline(), batch.source(), batch.initialPopulation(),
                boundedPopulation, boundedPopulation + " / " + batch.initialPopulation(), batch.ageDays(),
                batch.stageName(), batch.startDate(), batch.lastRecordedEventDate());
        List<SelectionReviewPayload.DataAvailabilityItem> availability = payload.dataAvailability() == null
                ? new ArrayList<>()
                : new ArrayList<>(payload.dataAvailability());
        if (availability.stream().noneMatch(item -> "Population reconciliation".equals(item.section()))) {
            availability.add(new SelectionReviewPayload.DataAvailabilityItem(
                    "Population reconciliation", "PARTIAL", population.sourceEventCount(),
                    batch.lastRecordedEventDate(), message));
        }
        return new SelectionReviewPayload(payload.reportTitle(), payload.disclaimer(), payload.payloadVersion(),
                payload.periodStart(), payload.periodEnd(), payload.asOfDate(), safeBatch, safePopulation,
                payload.healthEvents(), payload.productUse(), payload.incubation(), payload.finance(), availability,
                payload.selectionSummary(), payload.reviewInstructions());
    }

    private byte[] renderPdf(SelectionReviewResponse response) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        Document document = new Document();
        try {
            PdfWriter.getInstance(document, output);
            document.open();
            SelectionReviewPayload payload = response.payload();
            document.add(new Paragraph("Poultry Prophet · Batch Review"));
            document.add(new Paragraph(payload.batch().batchName() + " | " + response.periodStart() + " to " + response.periodEnd()));
            document.add(new Paragraph("Population: " + payload.batch().currentPopulationDisplay()
                    + " | Stage: " + payload.batch().stageName() + " | Day " + payload.batch().ageDays()));
            if (payload.population().reconciliationRequired()) {
                document.add(new Paragraph("Population record warning: "
                        + payload.population().reconciliationMessage()));
            }
            document.add(new Paragraph("Recorded data only. This report supports human review; it does not diagnose, predict, rank, or automatically select birds."));
            document.add(new Paragraph("\nSummary"));
            if (payload.selectionSummary() == null) {
                document.add(new Paragraph("Selection outcome: No finalized batch-level selection session recorded by the report date."));
            } else {
                SelectionReviewPayload.SelectionSummary selection = payload.selectionSummary();
                document.add(new Paragraph("Selection outcome: " + selection.acceptedCount() + " accepted of "
                        + selection.evaluatedCount() + " evaluated ("
                        + (selection.selectionRatePercent() == null ? "rate unavailable" : selection.selectionRatePercent() + "%") + ")"));
                document.add(new Paragraph("Continue observation: " + selection.continueObservationCount()
                        + " | Not accepted: " + selection.notAcceptedCount() + " | Other: " + selection.otherCount()));
                if (selection.criteriaNotes() != null && !selection.criteriaNotes().isBlank()) {
                    document.add(new Paragraph("Criteria notes: " + selection.criteriaNotes()));
                }
                document.add(new Paragraph("This batch-level review does not change the recorded population automatically."));
            }
            document.add(new Paragraph("Health-related deaths: " + payload.population().healthRelatedDeaths()
                    + " (" + (payload.population().healthRelatedLossPercentage() == null ? "rate unavailable" : payload.population().healthRelatedLossPercentage() + "% of start") + ")"));
            document.add(new Paragraph("Health events: " + payload.healthEvents().size() + " | Products used: " + payload.productUse().size()));
            document.add(new Paragraph("\nPopulation changes"));
            payload.population().categoryCounts().forEach((key, value) -> {
                if (value == null || value == 0) return;
                try { document.add(new Paragraph(key + ": " + value)); }
                catch (DocumentException exception) { throw new PdfRenderException(exception); }
            });
            document.add(new Paragraph("\nProducts used"));
            if (payload.productUse().isEmpty()) {
                document.add(new Paragraph("No product-use record was found in this period."));
            } else {
                Map<String, List<SelectionReviewPayload.ProductUseItem>> grouped = new LinkedHashMap<>();
                payload.productUse().forEach(item -> grouped.computeIfAbsent(item.productType() + " · " + item.brandName() + " · " + (item.unit() == null ? "unit" : item.unit()), ignored -> new ArrayList<>()).add(item));
                grouped.forEach((key, items) -> {
                    try {
                        double total = items.stream().filter(item -> item.quantity() != null).mapToDouble(SelectionReviewPayload.ProductUseItem::quantity).sum();
                        boolean complete = items.stream().allMatch(item -> item.quantity() != null);
                        document.add(new Paragraph(key + ": " + (complete ? total : items.size() + " recorded use(s), quantity incomplete")));
                    } catch (DocumentException exception) { throw new PdfRenderException(exception); }
                });
            }
            if (payload.finance() != null) {
                document.add(new Paragraph("\nRecorded batch finance"));
                document.add(new Paragraph("Income: " + payload.finance().recordedIncome()
                        + " | Expense: " + payload.finance().recordedExpense()
                        + " | Net cash flow: " + payload.finance().recordedNetCashFlow()));
                document.add(new Paragraph("Recorded cash-flow entries only; this is not accounting profit."));
            }
            document.add(new Paragraph("\nManager review"));
            document.add(new Paragraph("Status: " + response.reviewStatus() + " | Notes: "
                    + (response.managerNotes() == null ? "None" : response.managerNotes())));
            document.close();
            return output.toByteArray();
        } catch (DocumentException | PdfRenderException exception) {
            throw new IllegalStateException("Could not render selection review PDF", exception);
        }
    }

    private long populationDelta(BatchEvent event) {
        if (event.getPopulationDelta() != null) return event.getPopulationDelta();
        return switch (event.getEventType()) {
            case HEALTH_DEATH, MORTALITY, ACCIDENTAL_DEATH, SUSPECTED_PREDATION,
                    CONFIRMED_PREDATION, MISSING, TRANSFER_OUT, SALE, CULLING ->
                    -(long) event.getAffectedCount();
            case FOUND_RETURNED, TRANSFER_IN -> (long) event.getAffectedCount();
            default -> 0L;
        };
    }

    private Double round(double value, int scale) {
        return BigDecimal.valueOf(value).setScale(scale, RoundingMode.HALF_UP).doubleValue();
    }

    private String defaultPurpose(String value) {
        String purpose = clean(value);
        return purpose == null ? "ROUTINE_REVIEW" : purpose;
    }

    private String clean(String value) {
        if (value == null) return null;
        String cleaned = value.trim();
        return cleaned.isBlank() ? null : cleaned;
    }

    private record Dates(LocalDate periodStart, LocalDate periodEnd, LocalDate asOfDate) {}

    private static class PdfRenderException extends RuntimeException {
        private PdfRenderException(DocumentException cause) { super(cause); }
    }
}
