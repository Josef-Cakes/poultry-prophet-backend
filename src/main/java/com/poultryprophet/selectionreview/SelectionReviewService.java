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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
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
    public static final String PAYLOAD_VERSION = "selection-review-v1";

    private final BatchSelectionReviewRepository reviewRepository;
    private final BatchEventRepository eventRepository;
    private final FarmInputLogRepository inputRepository;
    private final IncubationCycleRepository incubationRepository;
    private final FinancialTransactionRepository financeRepository;
    private final BatchService batchService;
    private final ObjectMapper objectMapper;

    public SelectionReviewService(BatchSelectionReviewRepository reviewRepository,
                                  BatchEventRepository eventRepository,
                                  FarmInputLogRepository inputRepository,
                                  IncubationCycleRepository incubationRepository,
                                  FinancialTransactionRepository financeRepository,
                                  BatchService batchService,
                                  ObjectMapper objectMapper) {
        this.reviewRepository = reviewRepository;
        this.eventRepository = eventRepository;
        this.inputRepository = inputRepository;
        this.incubationRepository = incubationRepository;
        this.financeRepository = financeRepository;
        this.batchService = batchService;
        this.objectMapper = objectMapper;
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
        List<FarmInputLog> products = inputRepository.findByFarmIdAndBatchIdOrderByRecordedAtDesc(farmId, batch.getId())
                .stream().filter(value -> within(value.getRecordedAt(), periodStart, periodEnd)).toList();

        BatchService.StageView stage = batchService.resolveStage(batch);
        LocalDate latestEventDate = events.stream().map(BatchEvent::getEventDate).max(LocalDate::compareTo).orElse(null);
        SelectionReviewPayload.PopulationSummary population = populationSummary(batch, events);

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
        availability.add(availability("Health history", healthEvents.size(),
                healthEvents.stream().map(SelectionReviewPayload.HealthEventItem::eventDate).max(LocalDate::compareTo).orElse(null),
                "Only recorded observations and health-related events are shown; this is not a diagnosis."));
        availability.add(availability("Product-use history", productUse.size(),
                productUse.stream().map(value -> value.recordedAt().atZone(ZoneOffset.UTC).toLocalDate()).max(LocalDate::compareTo).orElse(null),
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

        SelectionReviewPayload.BatchOverview overview = new SelectionReviewPayload.BatchOverview(
                batch.getId(), batch.getName(), batch.getBloodline(), batch.getSource(), batch.getInitialPopulation(),
                batch.getCurrentPopulation(), batch.getCurrentPopulation() + " / " + batch.getInitialPopulation(),
                Math.max(1, ChronoUnit.DAYS.between(batch.getStartDate(), asOfDate)), stage.stage().getName(),
                batch.getStartDate(), latestEventDate);

        return new SelectionReviewPayload(
                "Batch Selection Review Report",
                "Descriptive decision support only. This report does not diagnose, predict performance, rank birds, or automatically select a bird.",
                PAYLOAD_VERSION, periodStart, periodEnd, asOfDate, overview, population, healthEvents, productUse,
                incubation, finance, availability,
                new SelectionReviewPayload.ReviewInstructions(ManagerReviewStatus.NOT_REVIEWED.name(), null, null, null, null));
    }

    private SelectionReviewPayload.PopulationSummary populationSummary(Batch batch, List<BatchEvent> events) {
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

        for (BatchEvent event : events) {
            long amount = Math.abs(event.getPopulationDelta() == null ? event.getAffectedCount() : event.getPopulationDelta());
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
        }

        long healthDeaths = categories.get("healthRelatedDeaths");
        Double rate = batch.getInitialPopulation() == 0 ? null
                : round(healthDeaths * 100.0 / batch.getInitialPopulation(), 2);
        return new SelectionReviewPayload.PopulationSummary(
                batch.getInitialPopulation(), batch.getCurrentPopulation(), healthDeaths, rate,
                categories.get("accidentalDeaths"), categories.get("predation"), categories.get("missing"),
                categories.get("returned"), categories.get("transfersOut"), categories.get("transfersIn"),
                categories.get("sales"), categories.get("culling"), categories.get("countCorrections"),
                categories.get("legacyMortalityRecords"), events.size(), categories);
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
        Instant from = start.atStartOfDay().toInstant(ZoneOffset.UTC);
        Instant until = end.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC);
        return !timestamp.isBefore(from) && timestamp.isBefore(until);
    }

    private Dates normalizeDates(Batch batch, LocalDate requestedStart, LocalDate requestedEnd, LocalDate requestedAsOf) {
        LocalDate end = requestedEnd == null ? LocalDate.now() : requestedEnd;
        LocalDate start = requestedStart == null ? batch.getStartDate() : requestedStart;
        LocalDate asOf = requestedAsOf == null ? end : requestedAsOf;
        if (start.isAfter(end)) throw new BadRequestException("periodStart must not be after periodEnd");
        if (asOf.isBefore(start) || asOf.isAfter(end)) throw new BadRequestException("asOfDate must be within the report period");
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
                    payload.reviewInstructions());
        }
        Instant sourceCutoff = review.getSourceCutoffAt() == null ? review.getGeneratedAt() : review.getSourceCutoffAt();
        boolean newerDataAvailable = sourceCutoff != null && (
                eventRepository.existsByBatchIdAndCreatedAtAfter(review.getBatchId(), sourceCutoff)
                        || inputRepository.existsByFarmIdAndBatchIdAndCreatedAtAfter(review.getFarmId(), review.getBatchId(), sourceCutoff)
                        || financeRepository.existsByFarmIdAndBatchIdAndCreatedAtAfter(review.getFarmId(), review.getBatchId(), sourceCutoff));
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
                payload.dataAvailability(), instructions);
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
            return objectMapper.readValue(json, SelectionReviewPayload.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not read selection review snapshot", exception);
        }
    }

    private byte[] renderPdf(SelectionReviewResponse response) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        Document document = new Document();
        try {
            PdfWriter.getInstance(document, output);
            document.open();
            SelectionReviewPayload payload = response.payload();
            document.add(new Paragraph(payload.reportTitle()));
            document.add(new Paragraph("Batch: " + payload.batch().batchName() + " | Period: "
                    + response.periodStart() + " to " + response.periodEnd()));
            document.add(new Paragraph("Report v" + response.versionNumber() + " | Purpose: "
                    + (response.purpose() == null ? "Routine review" : response.purpose())
                    + " | Generated: " + response.generatedAt()));
            document.add(new Paragraph("This report snapshot does not close the batch or stop future records."));
            document.add(new Paragraph(payload.disclaimer()));
            document.add(new Paragraph("\nBatch overview"));
            document.add(new Paragraph("Population: " + payload.batch().currentPopulationDisplay()
                    + " | Age: " + payload.batch().ageDays() + " days | Stage: " + payload.batch().stageName()));
            document.add(new Paragraph("\nPopulation changes"));
            payload.population().categoryCounts().forEach((key, value) -> {
                try { document.add(new Paragraph(key + ": " + value)); }
                catch (DocumentException exception) { throw new PdfRenderException(exception); }
            });
            document.add(new Paragraph("Health-related loss percentage: "
                    + (payload.population().healthRelatedLossPercentage() == null ? "Unavailable" : payload.population().healthRelatedLossPercentage() + "%")));
            document.add(new Paragraph("\nHealth and intervention history"));
            document.add(new Paragraph("Health events: " + payload.healthEvents().size()
                    + " | Product-use records: " + payload.productUse().size()));
            if (payload.finance() != null) {
                document.add(new Paragraph("\nRecorded finance"));
                document.add(new Paragraph("Income: " + payload.finance().recordedIncome()
                        + " | Expense: " + payload.finance().recordedExpense()
                        + " | Net cash flow: " + payload.finance().recordedNetCashFlow()));
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
