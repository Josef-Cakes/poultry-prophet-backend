package com.poultryprophet.analytics;

import com.poultryprophet.analytics.dto.BatchComparisonResponse;
import com.poultryprophet.batch.Batch;
import com.poultryprophet.batch.BatchRepository;
import com.poultryprophet.event.BatchEvent;
import com.poultryprophet.event.BatchEventRepository;
import com.poultryprophet.event.EventType;
import com.poultryprophet.input.FarmInputLog;
import com.poultryprophet.input.FarmInputLogRepository;
import com.poultryprophet.selectionsession.SelectionSessionResponse;
import com.poultryprophet.selectionsession.SelectionSessionService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class BatchComparisonService {
    private static final String SYNTHETIC_PREFIX = "[TEST COPY]";

    private final BatchRepository batchRepository;
    private final BatchEventRepository eventRepository;
    private final FarmInputLogRepository inputRepository;
    private final SelectionSessionService selectionSessionService;
    private final ZoneId farmZone;

    public BatchComparisonService(BatchRepository batchRepository,
                                  BatchEventRepository eventRepository,
                                  FarmInputLogRepository inputRepository,
                                  SelectionSessionService selectionSessionService,
                                  @Value("${app.time-zone:Asia/Manila}") String timeZone) {
        this.batchRepository = batchRepository;
        this.eventRepository = eventRepository;
        this.inputRepository = inputRepository;
        this.selectionSessionService = selectionSessionService;
        this.farmZone = ZoneId.of(timeZone);
    }

    @Transactional(readOnly = true)
    public BatchComparisonResponse compare(Long farmId, List<Long> requestedIds,
                                           int requestedWindowDays, String requestedOrigin) {
        if (requestedIds == null || requestedIds.size() < 2 || requestedIds.size() > 3) {
            throw new IllegalArgumentException("Choose two or three batches to compare");
        }
        int windowDays = Math.max(1, Math.min(requestedWindowDays, 365));
        String origin = normalizeOrigin(requestedOrigin);
        List<Long> distinctIds = requestedIds.stream().distinct().toList();
        if (distinctIds.size() != requestedIds.size()) {
            throw new IllegalArgumentException("Choose different batches to compare");
        }
        List<Batch> batches = distinctIds.stream()
                .map(id -> batchRepository.findByIdAndFarmId(id, farmId)
                        .orElseThrow(() -> new IllegalArgumentException("A selected batch was not found in this farm")))
                .toList();
        for (Batch batch : batches) {
            boolean synthetic = batch.getName() != null && batch.getName().startsWith(SYNTHETIC_PREFIX);
            if (("REAL".equals(origin) && synthetic) || ("SYNTHETIC".equals(origin) && !synthetic)) {
                throw new IllegalArgumentException("All selected batches must belong to the same data view");
            }
        }

        LocalDate today = LocalDate.now(farmZone);
        int availableWindowDays = batches.stream()
                .mapToInt(batch -> (int) Math.max(1, ChronoUnit.DAYS.between(batch.getStartDate(), today) + 1))
                .min().orElse(1);
        int effectiveDays = Math.min(windowDays, availableWindowDays);
        List<String> warnings = new ArrayList<>();
        if (effectiveDays < windowDays) {
            warnings.add("One or more batches are younger than the requested window; all batches were normalized to "
                    + effectiveDays + " days.");
        }
        if ("ALL".equals(origin)) warnings.add("This comparison includes both real and synthetic records.");

        List<BatchComparisonResponse.BatchComparisonRow> rows = batches.stream()
                .map(batch -> row(farmId, batch, effectiveDays)).toList();
        boolean mixedSelectionAvailability = rows.stream()
                .map(BatchComparisonResponse.BatchComparisonRow::selectionDataStatus)
                .distinct().count() > 1;
        if (mixedSelectionAvailability) warnings.add("Selection sessions are not available for every batch; compare them only where recorded.");
        return new BatchComparisonResponse(windowDays, effectiveDays, farmZone.getId(), warnings, rows);
    }

    private BatchComparisonResponse.BatchComparisonRow row(Long farmId, Batch batch, int windowDays) {
        LocalDate end = batch.getStartDate().plusDays(windowDays - 1L);
        List<BatchEvent> events = eventRepository.findByBatchIdAndEventDateBetweenOrderByEventDateAscCreatedAtAsc(
                batch.getId(), batch.getStartDate(), end);
        int population = batch.getInitialPopulation();
        long healthDeaths = 0;
        long healthConcerns = 0;
        Map<String, Long> changes = new LinkedHashMap<>();
        for (BatchEvent event : events) {
            population += populationDelta(event);
            if (event.getEventType() == EventType.HEALTH_DEATH) healthDeaths += event.getAffectedCount();
            if (event.getEventType() == EventType.HEALTH_CONCERN) healthConcerns++;
            String cause = cause(event.getEventType());
            if (cause != null) changes.merge(cause, cause.equals("countCorrections")
                    ? (long) (event.getPopulationDelta() == null ? 0 : event.getPopulationDelta())
                    : Math.abs((long) event.getAffectedCount()), Long::sum);
        }
        List<FarmInputLog> productRecords = inputRepository.findByFarmIdAndBatchIdOrderByRecordedAtDesc(farmId, batch.getId())
                .stream().filter(input -> within(input, batch.getStartDate(), end)).toList();
        SelectionSessionResponse selection = selectionSessionService.latestFinalized(batch.getId(), farmId, end);
        List<String> limitations = new ArrayList<>();
        if (events.isEmpty()) limitations.add("No event records for this common window.");
        if (productRecords.isEmpty()) limitations.add("No product-use records for this common window.");
        if (selection == null) limitations.add("No finalized selection session in this common window.");
        Double lossRate = batch.getInitialPopulation() == 0 ? null
                : round(healthDeaths * 100.0 / batch.getInitialPopulation());
        return new BatchComparisonResponse.BatchComparisonRow(
                batch.getId(), batch.getName(), batch.getBloodline(), batch.getSource(), batch.getStartDate(), end,
                batch.getInitialPopulation(), population, healthDeaths, lossRate, healthConcerns,
                productRecords.size(), changes,
                selection == null ? null : selection.evaluatedCount(), selection == null ? null : selection.acceptedCount(),
                selection == null ? null : selection.selectionRatePercent(), selection == null ? "NO_RECORD" : "AVAILABLE",
                events.size(), productRecords.size(), limitations);
    }

    private boolean within(FarmInputLog input, LocalDate start, LocalDate end) {
        if (input.getRecordedAt() == null) return false;
        return !input.getRecordedAt().isBefore(start.atStartOfDay(farmZone).toInstant())
                && input.getRecordedAt().isBefore(end.plusDays(1).atStartOfDay(farmZone).toInstant());
    }

    private int populationDelta(BatchEvent event) {
        if (event.getPopulationDelta() != null) return event.getPopulationDelta();
        return switch (event.getEventType()) {
            case HEALTH_DEATH, MORTALITY, ACCIDENTAL_DEATH, SUSPECTED_PREDATION,
                    CONFIRMED_PREDATION, MISSING, TRANSFER_OUT, SALE, CULLING -> -event.getAffectedCount();
            case FOUND_RETURNED, TRANSFER_IN -> event.getAffectedCount();
            default -> 0;
        };
    }

    private String cause(EventType type) {
        if (type == null) return null;
        return switch (type) {
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
    }

    private String normalizeOrigin(String value) {
        String normalized = value == null ? "REAL" : value.trim().toUpperCase(Locale.ROOT);
        if (!List.of("REAL", "SYNTHETIC", "ALL").contains(normalized)) return "REAL";
        return normalized;
    }

    private double round(double value) {
        return BigDecimal.valueOf(value).setScale(2, java.math.RoundingMode.HALF_UP).doubleValue();
    }
}
