package com.poultryprophet.analytics;

import com.poultryprophet.analytics.dto.FarmSummaryAnalyticsResponse;
import com.poultryprophet.batch.Batch;
import com.poultryprophet.batch.BatchRepository;
import com.poultryprophet.batch.BatchStatus;
import com.poultryprophet.event.BatchEvent;
import com.poultryprophet.event.BatchEventRepository;
import com.poultryprophet.event.EventType;
import com.poultryprophet.finance.FinanceTransactionStatus;
import com.poultryprophet.finance.FinanceTransactionType;
import com.poultryprophet.finance.FinancialTransaction;
import com.poultryprophet.finance.FinancialTransactionRepository;
import com.poultryprophet.incubation.IncubationCycle;
import com.poultryprophet.incubation.IncubationCycleRepository;
import com.poultryprophet.incubation.IncubationStatus;
import com.poultryprophet.input.FarmInputLog;
import com.poultryprophet.input.FarmInputLogRepository;
import com.poultryprophet.population.PopulationProjection;
import com.poultryprophet.population.PopulationProjectionService;
import com.poultryprophet.task.HandlerTask;
import com.poultryprophet.task.HandlerTaskRepository;
import com.poultryprophet.task.TaskStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

@Service
public class FarmSummaryAnalyticsService {
    private static final String SYNTHETIC_PREFIX = "[TEST COPY]";

    private final BatchRepository batchRepository;
    private final BatchEventRepository eventRepository;
    private final FinancialTransactionRepository financeRepository;
    private final HandlerTaskRepository taskRepository;
    private final IncubationCycleRepository incubationRepository;
    private final FarmInputLogRepository inputRepository;
    private final PopulationProjectionService populationProjectionService;
    private final ZoneId farmZone;

    public FarmSummaryAnalyticsService(BatchRepository batchRepository,
                                        BatchEventRepository eventRepository,
                                        FinancialTransactionRepository financeRepository,
                                        HandlerTaskRepository taskRepository,
                                        IncubationCycleRepository incubationRepository,
                                        FarmInputLogRepository inputRepository,
                                        PopulationProjectionService populationProjectionService,
                                        @Value("${app.time-zone:Asia/Manila}") String timeZone) {
        this.batchRepository = batchRepository;
        this.eventRepository = eventRepository;
        this.financeRepository = financeRepository;
        this.taskRepository = taskRepository;
        this.incubationRepository = incubationRepository;
        this.inputRepository = inputRepository;
        this.populationProjectionService = populationProjectionService;
        this.farmZone = ZoneId.of(timeZone);
    }

    @Transactional(readOnly = true)
    public FarmSummaryAnalyticsResponse summarize(Long farmId, String requestedScope,
                                                   Long requestedBatchId, LocalDate requestedStart,
                                                   LocalDate requestedEnd, String requestedOrigin,
                                                   String testRunId) {
        if (farmId == null) throw new IllegalArgumentException("Join a farm before viewing the farm summary");
        LocalDate end = requestedEnd == null ? LocalDate.now(farmZone) : requestedEnd;
        LocalDate start = requestedStart == null ? end.minusDays(29) : requestedStart;
        if (start.isAfter(end)) throw new IllegalArgumentException("Start date cannot be after end date");

        String scope = "BATCH".equalsIgnoreCase(requestedScope) ? "BATCH" : "FARM";
        boolean farmWide = "FARM".equals(scope);
        String origin = normaliseOrigin(requestedOrigin);
        List<Batch> allBatches = batchRepository.findByFarmIdAndStatusNotOrderByCreatedAtDesc(farmId, BatchStatus.ARCHIVED);
        Predicate<Batch> originFilter = batchOriginFilter(origin, testRunId);
        List<Batch> scopedBatches;
        Long batchId = null;
        String batchName = null;
        if ("BATCH".equals(scope)) {
            Batch selected = allBatches.stream().filter(batch -> Objects.equals(batch.getId(), requestedBatchId))
                    .findFirst().orElseThrow(() -> new IllegalArgumentException("Selected batch was not found in this farm"));
            if (!originFilter.test(selected)) throw new IllegalArgumentException("Selected batch is not in the requested data view");
            scopedBatches = List.of(selected);
            batchId = selected.getId();
            batchName = selected.getName();
        } else {
            scopedBatches = allBatches.stream().filter(originFilter).toList();
        }

        Set<Long> batchIds = scopedBatches.stream().map(Batch::getId).collect(Collectors.toSet());
        long activeBatches = scopedBatches.stream().filter(batch -> batch.getStatus() == BatchStatus.ACTIVE).count();
        long initialPopulation = scopedBatches.stream().mapToLong(Batch::getInitialPopulation).sum();
        LocalDate populationAsOf = LocalDate.now(farmZone);
        List<String> populationLimitations = new ArrayList<>();
        long currentPopulation = scopedBatches.stream().mapToLong(batch -> {
            List<BatchEvent> ledgerEvents = eventRepository
                    .findByBatchIdAndEventDateBetweenOrderByEventDateAscCreatedAtAsc(
                            batch.getId(), batch.getStartDate(), populationAsOf);
            PopulationProjection projection = populationProjectionService.project(
                    batch, ledgerEvents, populationAsOf, farmZone);
            if (projection.reconciliationRequired()) {
                populationLimitations.add("Population records need review for batch '" + batch.getName() + "'.");
                return projection.boundedPopulation();
            }
            return projection.validPopulation() == null ? projection.boundedPopulation() : projection.validPopulation();
        }).sum();
        FarmSummaryAnalyticsResponse.PopulationSummary population =
                new FarmSummaryAnalyticsResponse.PopulationSummary(activeBatches, initialPopulation, currentPopulation, !scopedBatches.isEmpty());

        List<BatchEvent> events = new ArrayList<>();
        for (Long id : batchIds) {
            events.addAll(eventRepository.findByBatchIdAndEventDateBetweenOrderByEventDateAscCreatedAtAsc(id, start, end));
        }
        List<FarmInputLog> inputs = inputRepository.findByFarmIdAndRecordedAtBetweenOrderByRecordedAtAsc(
                        farmId, start.atStartOfDay(farmZone).toInstant(), end.plusDays(1).atStartOfDay(farmZone).toInstant())
                .stream().filter(input -> scopeMatches(input.getBatchId(), batchIds, farmWide)).toList();
        Map<String, Long> inputTypes = inputs.stream().collect(Collectors.groupingBy(
                input -> input.getProductType().name(), LinkedHashMap::new, Collectors.counting()));

        List<FinancialTransaction> finance = financeRepository.findByFarmIdAndTransactionDateBetweenOrderByTransactionDateAsc(farmId, start, end)
                .stream().filter(tx -> scopeMatches(tx.getBatchId(), batchIds, farmWide)).toList();
        List<HandlerTask> taskRecords = taskRepository.findByFarmIdOrderByDueAtAscCreatedAtDesc(farmId).stream()
                .filter(task -> scopeMatches(task.getBatchId(), batchIds, farmWide)).filter(task -> overlaps(task, start, end)).toList();
        List<IncubationCycle> cycles = incubationRepository.findByFarmIdAndLoadedDateBetweenOrderByLoadedDateAsc(farmId, start, end).stream()
                .filter(cycle -> scopeMatches(cycle.getCreatedBatchId(), batchIds, farmWide))
                .filter(cycle -> originMatches(cycle.getNotes(), origin, testRunId)).toList();

        FarmSummaryAnalyticsResponse.FinanceSummary financeSummary = finance(finance, start, end, farmWide);
        List<String> limitations = new ArrayList<>();
        limitations.addAll(populationLimitations);
        if (scopedBatches.isEmpty()) limitations.add("No batches match this view.");
        if (batchId != null && financeSummary.available()) limitations.add(financeSummary.limitation());
        if (!cycles.isEmpty() && cycles.stream().anyMatch(cycle -> cycle.getStatus() != IncubationStatus.COMPLETED)) {
            limitations.add("In-progress incubation cycles are excluded from the finalized hatch rate.");
        }
        if (inputs.isEmpty()) limitations.add("No feed, vitamin, medicine, or vaccine records in this period.");
        if (events.isEmpty()) limitations.add("No batch events were recorded in this period.");

        return new FarmSummaryAnalyticsResponse(scope, batchId, batchName, start, end, farmZone.getId(), Instant.now(),
                population, eventSummary(events), activity(events, start, end), financeSummary,
                tasks(taskRecords), incubation(cycles), inputTypes, limitations);
    }

    private Predicate<Batch> batchOriginFilter(String origin, String testRunId) {
        return batch -> {
            boolean synthetic = batch.getName() != null && batch.getName().startsWith(SYNTHETIC_PREFIX);
            boolean matchesOrigin = switch (origin) {
                case "SYNTHETIC" -> synthetic;
                case "ALL" -> true;
                default -> !synthetic;
            };
            return matchesOrigin && (testRunId == null || testRunId.isBlank()
                    || (batch.getName() != null && batch.getName().contains(testRunId))
                    || (batch.getSource() != null && batch.getSource().contains(testRunId)));
        };
    }

    private String normaliseOrigin(String value) {
        if (value == null) return "REAL";
        String cleaned = value.trim().toUpperCase(Locale.ROOT);
        return Set.of("REAL", "SYNTHETIC", "ALL").contains(cleaned) ? cleaned : "REAL";
    }

    private boolean originMatches(String text, String origin, String testRunId) {
        if ("REAL".equals(origin)) return text == null || !text.contains("SYNTHETIC");
        if ("ALL".equals(origin)) return true;
        return text != null && text.contains("SYNTHETIC")
                && (testRunId == null || testRunId.isBlank() || text.contains(testRunId));
    }

    private boolean scopeMatches(Long recordBatchId, Set<Long> batchIds, boolean farmScope) {
        return batchIds.contains(recordBatchId) || (farmScope && recordBatchId == null);
    }

    private FarmSummaryAnalyticsResponse.EventSummary eventSummary(List<BatchEvent> events) {
        long healthDeaths = events.stream().filter(event -> event.getEventType().isHealthMortality())
                .mapToLong(BatchEvent::getAffectedCount).sum();
        long healthConcerns = events.stream().filter(event -> event.getEventType() == EventType.HEALTH_CONCERN).count();
        long interventions = events.stream().filter(event -> event.getEventType() == EventType.VACCINE_MEDICINE).count();
        long otherLosses = events.stream().filter(this::isOtherLoss).mapToLong(BatchEvent::getAffectedCount).sum();
        long birdsAffected = events.stream().mapToLong(BatchEvent::getAffectedCount).sum();
        return new FarmSummaryAnalyticsResponse.EventSummary(healthDeaths, otherLosses, healthConcerns, interventions, events.size(), birdsAffected);
    }

    private boolean isOtherLoss(BatchEvent event) {
        EventType type = event.getEventType();
        return type != null && type.isPopulationLedgerEvent() && !type.isHealthMortality()
                && event.getAffectedCount() > 0 && type != EventType.FOUND_RETURNED && type != EventType.TRANSFER_IN;
    }

    private List<FarmSummaryAnalyticsResponse.ActivityBucket> activity(List<BatchEvent> events, LocalDate start, LocalDate end) {
        boolean weekly = ChronoUnit.DAYS.between(start, end) + 1 > 31;
        Map<LocalDate, ActivityCounter> counters = new LinkedHashMap<>();
        LocalDate cursor = start;
        while (!cursor.isAfter(end)) {
            LocalDate bucket = weekly ? cursor.with(java.time.DayOfWeek.MONDAY) : cursor;
            counters.putIfAbsent(bucket, new ActivityCounter());
            cursor = weekly ? bucket.plusWeeks(1) : cursor.plusDays(1);
        }
        for (BatchEvent event : events) {
            LocalDate bucket = weekly ? event.getEventDate().with(java.time.DayOfWeek.MONDAY) : event.getEventDate();
            ActivityCounter counter = counters.computeIfAbsent(bucket, ignored -> new ActivityCounter());
            if (event.getEventType() == EventType.HEALTH_CONCERN) counter.healthConcerns++;
            else if (event.getEventType().isHealthMortality()) counter.healthDeaths++;
            else if (event.getEventType() == EventType.VACCINE_MEDICINE) counter.interventions++;
            else if (isOtherLoss(event)) counter.otherLosses++;
            else counter.otherEvents++;
        }
        DateTimeFormatter format = DateTimeFormatter.ofPattern("MMM d");
        return counters.entrySet().stream().sorted(Map.Entry.comparingByKey()).map(entry -> {
            LocalDate bucket = entry.getKey();
            LocalDate bucketEnd = weekly ? bucket.plusDays(6) : bucket;
            if (bucketEnd.isAfter(end)) bucketEnd = end;
            ActivityCounter c = entry.getValue();
            return new FarmSummaryAnalyticsResponse.ActivityBucket(bucket, bucketEnd, bucket.format(format), "events",
                    c.healthConcerns, c.healthDeaths, c.otherLosses, c.interventions, c.otherEvents);
        }).toList();
    }

    private FarmSummaryAnalyticsResponse.FinanceSummary finance(List<FinancialTransaction> values,
                                                                  LocalDate start, LocalDate end, boolean farmScope) {
        Map<LocalDate, MoneyCounter> buckets = new HashMap<>();
        Map<String, BigDecimal> categories = new HashMap<>();
        BigDecimal income = BigDecimal.ZERO;
        BigDecimal expense = BigDecimal.ZERO;
        long posted = 0;
        for (FinancialTransaction tx : values) {
            if (tx.getStatus() != FinanceTransactionStatus.POSTED) continue;
            posted++;
            if (tx.getType() == FinanceTransactionType.INCOME) income = income.add(tx.getAmount());
            else {
                expense = expense.add(tx.getAmount());
                categories.merge(tx.getCategory(), tx.getAmount(), BigDecimal::add);
            }
            LocalDate bucket = bucketStart(tx.getTransactionDate(), start, end);
            MoneyCounter counter = buckets.computeIfAbsent(bucket, ignored -> new MoneyCounter());
            if (tx.getType() == FinanceTransactionType.INCOME) counter.income = counter.income.add(tx.getAmount());
            else counter.expense = counter.expense.add(tx.getAmount());
        }
        List<FarmSummaryAnalyticsResponse.CashFlowBucket> series = buckets.entrySet().stream()
                .sorted(Map.Entry.comparingByKey()).map(entry -> {
                    MoneyCounter c = entry.getValue();
                    LocalDate periodEnd = bucketEnd(entry.getKey(), start, end);
                    return new FarmSummaryAnalyticsResponse.CashFlowBucket(entry.getKey(), periodEnd,
                            entry.getKey().format(DateTimeFormatter.ofPattern("MMM d")), money(c.income),
                            money(c.expense), money(c.income.subtract(c.expense)));
                }).toList();
        List<FarmSummaryAnalyticsResponse.CategoryTotal> categoryTotals = categories.entrySet().stream()
                .sorted(Map.Entry.<String, BigDecimal>comparingByValue(Comparator.reverseOrder())).limit(8)
                .map(entry -> new FarmSummaryAnalyticsResponse.CategoryTotal(entry.getKey(), money(entry.getValue()))).toList();
        String currency = values.stream().map(FinancialTransaction::getCurrency).filter(Objects::nonNull).findFirst().orElse("PHP");
        String limitation = farmScope ? "Farm view includes batch-linked and farm-wide records."
                : "Batch view includes only transactions linked to this batch; farm-wide costs are excluded.";
        return new FarmSummaryAnalyticsResponse.FinanceSummary(currency, money(income), money(expense),
                money(income.subtract(expense)), posted, series, categoryTotals, posted > 0, limitation);
    }

    private LocalDate bucketStart(LocalDate date, LocalDate start, LocalDate end) {
        long span = ChronoUnit.DAYS.between(start, end) + 1;
        if (span <= 31) return date;
        if (span <= 180) return date.with(java.time.DayOfWeek.MONDAY);
        return date.withDayOfMonth(1);
    }

    private LocalDate bucketEnd(LocalDate bucket, LocalDate start, LocalDate end) {
        long span = ChronoUnit.DAYS.between(start, end) + 1;
        LocalDate result = span <= 31 ? bucket : span <= 180 ? bucket.plusDays(6) : bucket.plusMonths(1).minusDays(1);
        return result.isAfter(end) ? end : result;
    }

    private FarmSummaryAnalyticsResponse.TaskSummary tasks(List<HandlerTask> tasks) {
        long completed = tasks.stream().filter(task -> task.getStatus() == TaskStatus.COMPLETED).count();
        long open = tasks.stream().filter(task -> task.getStatus() != TaskStatus.COMPLETED && task.getStatus() != TaskStatus.CANCELLED).count();
        long overdue = tasks.stream().filter(task -> task.getDueAt() != null
                        && task.getDueAt().atZone(farmZone).toLocalDate().isBefore(LocalDate.now(farmZone)))
                .filter(task -> task.getStatus() != TaskStatus.COMPLETED && task.getStatus() != TaskStatus.CANCELLED).count();
        long denominator = tasks.stream().filter(task -> task.getStatus() != TaskStatus.CANCELLED).count();
        return new FarmSummaryAnalyticsResponse.TaskSummary(tasks.size(), open, completed, overdue,
                denominator == 0 ? 0 : round(completed * 100.0 / denominator), !tasks.isEmpty());
    }

    private boolean overlaps(HandlerTask task, LocalDate start, LocalDate end) {
        LocalDate created = task.getCreatedAt().atZone(farmZone).toLocalDate();
        LocalDate due = task.getDueAt() == null ? created : task.getDueAt().atZone(farmZone).toLocalDate();
        return !due.isBefore(start) && !created.isAfter(end);
    }

    private FarmSummaryAnalyticsResponse.IncubationSummary incubation(List<IncubationCycle> cycles) {
        List<IncubationCycle> completed = cycles.stream().filter(cycle -> cycle.getStatus() == IncubationStatus.COMPLETED).toList();
        int eggs = completed.stream().mapToInt(IncubationCycle::getEggsLoaded).sum();
        int hatched = completed.stream().mapToInt(cycle -> cycle.getHatchedCount() == null ? 0 : cycle.getHatchedCount()).sum();
        double duration = completed.stream().filter(cycle -> cycle.getActualHatchDate() != null)
                .mapToLong(cycle -> ChronoUnit.DAYS.between(cycle.getLoadedDate(), cycle.getActualHatchDate())).average().orElse(0);
        long inProgress = cycles.stream().filter(cycle -> cycle.getStatus() != IncubationStatus.COMPLETED && cycle.getStatus() != IncubationStatus.CANCELLED).count();
        return new FarmSummaryAnalyticsResponse.IncubationSummary(cycles.size(), completed.size(), inProgress,
                eggs, hatched, eggs == 0 ? 0 : round(hatched * 100.0 / eggs), round(duration), !cycles.isEmpty());
    }

    private String money(BigDecimal value) {
        return value.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString();
    }

    private double round(double value) { return Math.round(value * 10.0) / 10.0; }

    private static final class ActivityCounter {
        long healthConcerns;
        long healthDeaths;
        long otherLosses;
        long interventions;
        long otherEvents;
    }

    private static final class MoneyCounter {
        BigDecimal income = BigDecimal.ZERO;
        BigDecimal expense = BigDecimal.ZERO;
    }
}
