package com.poultryprophet.finance;

import com.poultryprophet.batch.BatchService;
import com.poultryprophet.batch.Batch;
import com.poultryprophet.common.BadRequestException;
import com.poultryprophet.common.NotFoundException;
import com.poultryprophet.finance.dto.*;
import com.poultryprophet.incubation.IncubationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.ArrayList;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class FinanceService {
    private final FinancialTransactionRepository repository;
    private final BatchService batchService;
    private final IncubationService incubationService;

    public FinanceService(FinancialTransactionRepository repository, BatchService batchService,
                          IncubationService incubationService) {
        this.repository = repository; this.batchService = batchService; this.incubationService = incubationService;
    }

    @Transactional
    public FinancialTransactionResponse create(Long farmId, Long userId, CreateFinancialTransactionRequest req) {
        if (farmId == null) throw new BadRequestException("Join a farm first");
        validateParent(farmId, req.batchId(), req.incubationCycleId());
        FinancialTransaction tx = new FinancialTransaction();
        tx.setFarmId(farmId); tx.setBatchId(req.batchId()); tx.setIncubationCycleId(req.incubationCycleId());
        tx.setTransactionDate(req.transactionDate()); tx.setType(req.type()); tx.setCategory(req.category().trim());
        tx.setAmount(req.amount()); tx.setCurrency(req.currency() == null || req.currency().isBlank() ? "PHP" : req.currency().trim().toUpperCase());
        tx.setCounterparty(trim(req.counterparty())); tx.setDescription(trim(req.description())); tx.setEnteredBy(userId);
        return FinancialTransactionResponse.from(repository.save(tx));
    }

    @Transactional(readOnly = true)
    public List<FinancialTransactionResponse> list(Long farmId, LocalDate start, LocalDate end) {
        List<FinancialTransaction> values = start != null && end != null
                ? repository.findByFarmIdAndTransactionDateBetweenOrderByTransactionDateAsc(farmId, start, end)
                : repository.findByFarmIdOrderByTransactionDateDescCreatedAtDesc(farmId);
        return values.stream().map(FinancialTransactionResponse::from).toList();
    }

    @Transactional
    public FinancialTransactionResponse voidTransaction(Long id, Long farmId, String reason) {
        FinancialTransaction tx = repository.findByIdAndFarmId(id, farmId)
                .orElseThrow(() -> new NotFoundException("Financial transaction " + id + " not found"));
        if (tx.getStatus() == FinanceTransactionStatus.VOIDED) throw new BadRequestException("Transaction is already voided");
        tx.setStatus(FinanceTransactionStatus.VOIDED); tx.setVoidReason(trim(reason));
        return FinancialTransactionResponse.from(repository.save(tx));
    }

    @Transactional(readOnly = true)
    public FinanceSummaryResponse summary(Long farmId, LocalDate start, LocalDate end) {
        LocalDate effectiveEnd = end == null ? LocalDate.now() : end;
        LocalDate effectiveStart = start == null ? effectiveEnd.minusDays(30) : start;
        if (effectiveStart.isAfter(effectiveEnd)) throw new BadRequestException("Start date cannot be after end date");
        List<FinancialTransaction> values = repository.findByFarmIdAndTransactionDateBetweenOrderByTransactionDateAsc(farmId, effectiveStart, effectiveEnd);
        BigDecimal income = values.stream().filter(v -> v.getStatus() == FinanceTransactionStatus.POSTED && v.getType() == FinanceTransactionType.INCOME).map(FinancialTransaction::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal expense = values.stream().filter(v -> v.getStatus() == FinanceTransactionStatus.POSTED && v.getType() == FinanceTransactionType.EXPENSE).map(FinancialTransaction::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new FinanceSummaryResponse(effectiveStart, effectiveEnd, income, expense, income.subtract(expense), values.size());
    }

    @Transactional(readOnly = true)
    public FinanceAnalyticsResponse analytics(Long farmId, Long batchId, LocalDate start, LocalDate end) {
        LocalDate effectiveEnd = end == null ? LocalDate.now() : end;
        LocalDate effectiveStart;
        String scope;
        String batchName = null;
        List<FinancialTransaction> values;

        if (batchId != null) {
            Batch batch = batchService.requireBatch(batchId, farmId);
            effectiveStart = start == null ? batch.getStartDate() : start;
            scope = "BATCH";
            batchName = batch.getName();
            values = repository.findByFarmIdAndBatchIdAndTransactionDateBetweenOrderByTransactionDateAsc(
                    farmId, batchId, effectiveStart, effectiveEnd);
        } else {
            effectiveStart = start == null ? effectiveEnd.minusMonths(12) : start;
            scope = "FARM";
            values = repository.findByFarmIdAndTransactionDateBetweenOrderByTransactionDateAsc(
                    farmId, effectiveStart, effectiveEnd);
        }

        if (effectiveStart.isAfter(effectiveEnd)) {
            throw new BadRequestException("Start date cannot be after end date");
        }

        long spanDays = ChronoUnit.DAYS.between(effectiveStart, effectiveEnd) + 1;
        Map<LocalDate, BigDecimal> incomeByBucket = new LinkedHashMap<>();
        Map<LocalDate, BigDecimal> expenseByBucket = new LinkedHashMap<>();
        Map<String, BigDecimal> expenseByCategory = new LinkedHashMap<>();
        BigDecimal income = BigDecimal.ZERO;
        BigDecimal expense = BigDecimal.ZERO;
        long postedCount = 0;
        long voidedCount = 0;

        for (FinancialTransaction tx : values) {
            if (tx.getStatus() == FinanceTransactionStatus.VOIDED) {
                voidedCount++;
                continue;
            }
            if (tx.getStatus() != FinanceTransactionStatus.POSTED) continue;
            postedCount++;
            LocalDate bucket = bucketStart(tx.getTransactionDate(), spanDays);
            if (tx.getType() == FinanceTransactionType.INCOME) {
                income = income.add(tx.getAmount());
                incomeByBucket.merge(bucket, tx.getAmount(), BigDecimal::add);
            } else {
                expense = expense.add(tx.getAmount());
                expenseByBucket.merge(bucket, tx.getAmount(), BigDecimal::add);
                expenseByCategory.merge(tx.getCategory(), tx.getAmount(), BigDecimal::add);
            }
        }

        List<LocalDate> bucketStarts = new ArrayList<>();
        bucketStarts.addAll(incomeByBucket.keySet());
        expenseByBucket.keySet().stream().filter(value -> !bucketStarts.contains(value)).forEach(bucketStarts::add);
        bucketStarts.sort(LocalDate::compareTo);
        BigDecimal cumulative = BigDecimal.ZERO;
        DateTimeFormatter labelFormat = spanDays <= 180
                ? DateTimeFormatter.ofPattern("MMM d")
                : DateTimeFormatter.ofPattern("MMM yyyy");
        List<FinanceAnalyticsResponse.SeriesPoint> series = new ArrayList<>();
        for (LocalDate bucket : bucketStarts) {
            BigDecimal bucketIncome = incomeByBucket.getOrDefault(bucket, BigDecimal.ZERO);
            BigDecimal bucketExpense = expenseByBucket.getOrDefault(bucket, BigDecimal.ZERO);
            BigDecimal netChange = bucketIncome.subtract(bucketExpense);
            cumulative = cumulative.add(netChange);
            LocalDate periodEnd = bucketEnd(bucket, spanDays, effectiveEnd);
            series.add(new FinanceAnalyticsResponse.SeriesPoint(
                    bucket, periodEnd, bucket.format(labelFormat), bucketIncome, bucketExpense,
                    netChange, cumulative));
        }

        List<FinanceAnalyticsResponse.CategoryTotal> categoryTotals = expenseByCategory.entrySet().stream()
                .sorted(Map.Entry.<String, BigDecimal>comparingByValue(Comparator.reverseOrder()))
                .limit(8)
                .map(entry -> new FinanceAnalyticsResponse.CategoryTotal(entry.getKey(), entry.getValue()))
                .toList();
        String currency = values.stream().map(FinancialTransaction::getCurrency).filter(value -> value != null && !value.isBlank())
                .findFirst().orElse("PHP");
        String limitation = batchId == null
                ? "Farm view includes batch-linked and farm-wide records."
                : "Batch view includes only transactions linked to this batch; farm-wide costs are excluded.";
        return new FinanceAnalyticsResponse(scope, batchId, batchName, effectiveStart, effectiveEnd, currency,
                new FinanceAnalyticsResponse.Totals(income, expense, income.subtract(expense), postedCount, voidedCount),
                series, categoryTotals, new FinanceAnalyticsResponse.Limitations(batchId != null, limitation));
    }

    private LocalDate bucketStart(LocalDate date, long spanDays) {
        if (spanDays <= 31) return date;
        if (spanDays <= 180) return date.with(java.time.DayOfWeek.MONDAY);
        return date.withDayOfMonth(1);
    }

    private LocalDate bucketEnd(LocalDate bucket, long spanDays, LocalDate maximum) {
        LocalDate end = spanDays <= 31
                ? bucket
                : spanDays <= 180
                ? bucket.plusDays(6)
                : bucket.plusMonths(1).minusDays(1);
        return end.isAfter(maximum) ? maximum : end;
    }
    private void validateParent(Long farmId, Long batchId, Long cycleId) {
        if (batchId != null) batchService.requireBatch(batchId, farmId);
        if (cycleId != null) incubationService.require(cycleId, farmId);
    }
    private String trim(String value) { return value == null || value.isBlank() ? null : value.trim(); }
}
