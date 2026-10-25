package com.poultryprophet.analytics;

import com.poultryprophet.analytics.dto.*;
import com.poultryprophet.finance.FinanceService;
import com.poultryprophet.finance.dto.FinanceSummaryResponse;
import com.poultryprophet.incubation.IncubationCycle;
import com.poultryprophet.incubation.IncubationCycleRepository;
import com.poultryprophet.incubation.IncubationMetrics;
import com.poultryprophet.incubation.IncubationStatus;
import com.poultryprophet.input.FarmInputLog;
import com.poultryprophet.input.FarmInputLogRepository;
import com.poultryprophet.task.HandlerTask;
import com.poultryprophet.task.HandlerTaskRepository;
import com.poultryprophet.task.TaskStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class OperationsAnalyticsService {
    private final IncubationCycleRepository incubationRepository;
    private final HandlerTaskRepository taskRepository;
    private final FarmInputLogRepository inputRepository;
    private final FinanceService financeService;

    public OperationsAnalyticsService(IncubationCycleRepository incubationRepository,
                                      HandlerTaskRepository taskRepository,
                                      FarmInputLogRepository inputRepository,
                                      FinanceService financeService) {
        this.incubationRepository = incubationRepository; this.taskRepository = taskRepository;
        this.inputRepository = inputRepository; this.financeService = financeService;
    }

    @Transactional(readOnly = true)
    public OperationsAnalyticsResponse summarize(Long farmId, LocalDate start, LocalDate end) {
        LocalDate effectiveEnd = end == null ? LocalDate.now() : end;
        LocalDate effectiveStart = start == null ? effectiveEnd.minusDays(30) : start;
        if (effectiveStart.isAfter(effectiveEnd)) throw new IllegalArgumentException("Start date cannot be after end date");

        List<IncubationCycle> loadedCycles = incubationRepository
                .findByFarmIdAndLoadedDateBetweenOrderByLoadedDateAsc(farmId, effectiveStart, effectiveEnd);
        List<IncubationCycle> completedCycles = incubationRepository
                .findByFarmIdAndStatusAndActualHatchDateBetweenOrderByActualHatchDateAsc(
                        farmId, IncubationStatus.COMPLETED, effectiveStart, effectiveEnd);
        Map<Long, IncubationCycle> cyclesById = new LinkedHashMap<>();
        loadedCycles.forEach(cycle -> cyclesById.put(cycle.getId(), cycle));
        completedCycles.forEach(cycle -> cyclesById.putIfAbsent(cycle.getId(), cycle));
        IncubationMetrics.Aggregate incubationTotals = IncubationMetrics.aggregate(completedCycles);
        IncubationAnalyticsResponse incubation = new IncubationAnalyticsResponse(
                effectiveStart, effectiveEnd, cyclesById.size(), completedCycles.size(),
                incubationTotals.eggsLoaded(), incubationTotals.hatched(), incubationTotals.unhatched(),
                incubationTotals.removedDamaged(), incubationTotals.hatchRatePercent(),
                incubationTotals.averageDurationDays());

        Instant startInstant = effectiveStart.atStartOfDay(java.time.ZoneOffset.UTC).toInstant();
        Instant endInstant = effectiveEnd.plusDays(1).atStartOfDay(java.time.ZoneOffset.UTC).toInstant();
        List<HandlerTask> tasks = taskRepository.findByFarmIdOrderByDueAtAscCreatedAtDesc(farmId).stream().filter(t -> overlaps(t, effectiveStart, effectiveEnd)).toList();
        long taskCompleted = tasks.stream().filter(t -> t.getStatus() == TaskStatus.COMPLETED).count();
        long overdue = tasks.stream().filter(t -> t.getDueAt() != null && t.getDueAt().isBefore(Instant.now()) && t.getStatus() != TaskStatus.COMPLETED && t.getStatus() != TaskStatus.CANCELLED).count();
        long open = tasks.stream().filter(t -> t.getStatus() != TaskStatus.COMPLETED && t.getStatus() != TaskStatus.CANCELLED).count();
        TaskAnalyticsResponse taskStats = new TaskAnalyticsResponse(effectiveStart, effectiveEnd, tasks.size(), open, taskCompleted, overdue, tasks.isEmpty() ? 0 : round(taskCompleted * 100.0 / tasks.size()));

        List<FarmInputLog> inputs = inputRepository.findByFarmIdAndRecordedAtBetweenOrderByRecordedAtAsc(farmId, startInstant, endInstant);
        InputUsageAnalyticsResponse inputStats = new InputUsageAnalyticsResponse(inputs.size(), countBy(inputs, i -> i.getProductType().name()), countBy(inputs, FarmInputLog::getBrandName));

        FinanceSummaryResponse finance = financeService.summary(farmId, effectiveStart, effectiveEnd);
        OperationsAnalyticsResponse.FinanceSummary financeStats = new OperationsAnalyticsResponse.FinanceSummary("PHP", finance.income().toPlainString(), finance.expense().toPlainString(), finance.net().toPlainString(), finance.transactionCount());
        return new OperationsAnalyticsResponse(effectiveStart, effectiveEnd, incubation, taskStats, inputStats, financeStats);
    }

    private boolean overlaps(HandlerTask task, LocalDate start, LocalDate end) {
        LocalDate created = task.getCreatedAt().atZone(java.time.ZoneOffset.UTC).toLocalDate();
        LocalDate due = task.getDueAt() == null ? created : task.getDueAt().atZone(java.time.ZoneOffset.UTC).toLocalDate();
        return !due.isBefore(start) && !created.isAfter(end);
    }
    private <T> Map<String, Long> countBy(List<T> values, Function<T, String> key) {
        return values.stream().map(key).filter(Objects::nonNull).collect(Collectors.groupingBy(Function.identity(), LinkedHashMap::new, Collectors.counting()));
    }
    private double round(double value) { return Math.round(value * 10.0) / 10.0; }
}
