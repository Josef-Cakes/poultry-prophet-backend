package com.poultryprophet.finance;

import com.poultryprophet.batch.BatchService;
import com.poultryprophet.batch.Batch;
import com.poultryprophet.common.BadRequestException;
import com.poultryprophet.common.NotFoundException;
import com.poultryprophet.common.PageResponse;
import com.poultryprophet.finance.dto.*;
import com.poultryprophet.event.dto.CreateBatchEventRequest;
import com.poultryprophet.incubation.IncubationService;
import com.poultryprophet.inventory.InventoryMovement;
import com.poultryprophet.inventory.InventoryMovementRepository;
import com.poultryprophet.inventory.InventoryMovementType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import jakarta.persistence.criteria.Predicate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.ArrayList;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.ArrayList;

@Service
public class FinanceService {
    private final FinancialTransactionRepository repository;
    private final BatchService batchService;
    private final IncubationService incubationService;
    private final InventoryMovementRepository inventoryMovementRepository;

    public FinanceService(FinancialTransactionRepository repository, BatchService batchService,
                          IncubationService incubationService, InventoryMovementRepository inventoryMovementRepository) {
        this.repository = repository; this.batchService = batchService; this.incubationService = incubationService;
        this.inventoryMovementRepository = inventoryMovementRepository;
    }

    @Transactional
    public FinancialTransactionResponse create(Long farmId, Long userId, CreateFinancialTransactionRequest req) {
        if (farmId == null) throw new BadRequestException("Join a farm first");
        if (req.sourceOperationId() != null
                || (req.sourceType() != null && !req.sourceType().isBlank()
                && !"MANUAL".equalsIgnoreCase(req.sourceType().trim()))) {
            throw new BadRequestException("Source metadata is managed by the system");
        }
        validateParent(farmId, req.batchId(), req.incubationCycleId());
        FinancialTransaction tx = new FinancialTransaction();
        tx.setFarmId(farmId); tx.setBatchId(req.batchId()); tx.setIncubationCycleId(req.incubationCycleId());
        tx.setTransactionDate(req.transactionDate()); tx.setType(req.type()); tx.setCategory(req.category().trim());
        tx.setAmount(req.amount()); tx.setCurrency(req.currency() == null || req.currency().isBlank() ? "PHP" : req.currency().trim().toUpperCase());
        tx.setCounterparty(trim(req.counterparty())); tx.setDescription(trim(req.description())); tx.setEnteredBy(userId);
        tx.setSourceType("MANUAL");
        tx.setSourceOperationId(null);
        return FinancialTransactionResponse.from(repository.save(tx));
    }

    /** Creates the auditable income side of a sale event exactly once. */
    @Transactional
    public FinancialTransactionResponse createSaleIncome(Long farmId, Long userId, Long batchId,
                                                         LocalDate transactionDate,
                                                         CreateBatchEventRequest.SaleDetails sale,
                                                         UUID operationId,
                                                         Long sourceEventId) {
        if (sale == null || sale.amount() == null || sale.amount().signum() <= 0) {
            throw new BadRequestException("A sale amount is required when recording sale proceeds");
        }
        String sourceOperationId = operationId == null ? null : operationId.toString();
        FinancialTransaction existing = sourceEventId == null ? null
                : repository.findByFarmIdAndSourceEventId(farmId, sourceEventId).orElse(null);
        if (existing == null && sourceOperationId != null) {
            existing = repository.findByFarmIdAndSourceOperationId(farmId, sourceOperationId).orElse(null);
        }
        if (existing != null) {
            if (!java.util.Objects.equals(existing.getBatchId(), batchId)
                    || (existing.getSourceEventId() != null
                    && !java.util.Objects.equals(existing.getSourceEventId(), sourceEventId))
                    || !"SALE_EVENT".equalsIgnoreCase(existing.getSourceType())
                    || existing.getType() != FinanceTransactionType.INCOME
                    || existing.getStatus() != FinanceTransactionStatus.POSTED
                    || existing.getAmount().compareTo(sale.amount()) != 0) {
                throw new com.poultryprophet.common.ConflictException(
                        "The sale is already linked to a different or voided financial transaction");
            }
            if (existing.getSourceEventId() == null && sourceEventId != null) {
                existing.setSourceEventId(sourceEventId);
                existing.setSourceType("SALE_EVENT");
                repository.save(existing);
            }
            return FinancialTransactionResponse.from(existing);
        }
        batchService.requireWritableBatch(batchId, farmId);
        FinancialTransaction tx = new FinancialTransaction();
        tx.setFarmId(farmId);
        tx.setBatchId(batchId);
        tx.setTransactionDate(transactionDate);
        tx.setType(FinanceTransactionType.INCOME);
        tx.setCategory("Bird sale");
        tx.setAmount(sale.amount());
        tx.setCurrency(sale.currency() == null || sale.currency().isBlank()
                ? "PHP" : sale.currency().trim().toUpperCase(java.util.Locale.ROOT));
        tx.setCounterparty(trim(sale.buyer()));
        tx.setDescription(trim(sale.reference()) == null ? "Sale proceeds from batch event"
                : "Sale proceeds; reference " + trim(sale.reference()));
        tx.setSourceType("SALE_EVENT");
        tx.setSourceEventId(sourceEventId);
        tx.setSourceOperationId(sourceOperationId);
        tx.setEnteredBy(userId);
        return FinancialTransactionResponse.from(repository.save(tx));
    }

    @Transactional(readOnly = true)
    public List<FinancialTransactionResponse> list(Long farmId, LocalDate start, LocalDate end) {
        List<FinancialTransaction> values = start != null && end != null
                ? repository.findByFarmIdAndTransactionDateBetweenOrderByTransactionDateAsc(farmId, start, end)
                : repository.findByFarmIdOrderByTransactionDateDescCreatedAtDesc(farmId);
        return values.stream().map(FinancialTransactionResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public PageResponse<FinancialTransactionResponse> page(Long farmId, LocalDate start, LocalDate end,
                                                            Long batchId, Boolean farmWide,
                                                            FinanceTransactionType type, int page, int size) {
        return page(farmId, start, end, batchId, farmWide, type, null, null, null, page, size);
    }

    @Transactional(readOnly = true)
    public PageResponse<FinancialTransactionResponse> page(Long farmId, LocalDate start, LocalDate end,
                                                            Long batchId, Boolean farmWide,
                                                            FinanceTransactionType type, String category,
                                                            String sourceType, FinanceTransactionStatus status,
                                                            int page, int size) {
        if (farmId == null) throw new BadRequestException("Join a farm first");
        int safePage = Math.max(0, page);
        int safeSize = Math.max(1, Math.min(size, 50));
        Specification<FinancialTransaction> specification = transactionSpecification(farmId, start, end, batchId,
                farmWide, type, category, sourceType, status);
        return PageResponse.from(repository.findAll(specification, PageRequest.of(safePage, safeSize,
                Sort.by(Sort.Order.desc("transactionDate"), Sort.Order.desc("createdAt"), Sort.Order.desc("id")))),
                FinancialTransactionResponse::from);
    }

    @Transactional
    public FinancialTransactionResponse voidTransaction(Long id, Long farmId, String reason) {
        FinancialTransaction tx = repository.findByIdAndFarmId(id, farmId)
                .orElseThrow(() -> new NotFoundException("Financial transaction " + id + " not found"));
        if (tx.getBatchId() != null) batchService.requireWritableBatch(tx.getBatchId(), farmId);
        if (tx.getStatus() == FinanceTransactionStatus.VOIDED) throw new BadRequestException("Transaction is already voided");
        if ("SALE_EVENT".equalsIgnoreCase(tx.getSourceType())) {
            throw new BadRequestException("Sale income must be corrected through the linked sale correction workflow");
        }
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
        return analytics(farmId, batchId, start, end, null, false);
    }

    @Transactional(readOnly = true)
    public FinanceAnalyticsResponse analytics(Long farmId, Long batchId, LocalDate start, LocalDate end,
                                              FinanceTransactionType type, boolean farmWide) {
        return analytics(farmId, batchId, start, end, type, farmWide, null, null, null);
    }

    @Transactional(readOnly = true)
    public FinanceAnalyticsResponse analytics(Long farmId, Long batchId, LocalDate start, LocalDate end,
                                              FinanceTransactionType type, boolean farmWide, String category,
                                              String sourceType, FinanceTransactionStatus status) {
        return analyticsInternal(farmId, batchId, start, end, type, farmWide, category, sourceType, status,
                null, false);
    }

    /**
     * Calculates the farm-level financial snapshot for another analytics view.
     * The supplied batch IDs preserve validation/origin filtering while still
     * including general farm records. Finance remains the single calculation
     * path for both the full Finance page and the Farm Overview preview.
     */
    @Transactional(readOnly = true)
    public FinanceAnalyticsResponse analyticsForFarmScope(Long farmId, Set<Long> includedBatchIds,
                                                           LocalDate start, LocalDate end) {
        return analyticsInternal(farmId, null, start, end, null, false, null, null, null,
                includedBatchIds, true);
    }

    private FinanceAnalyticsResponse analyticsInternal(Long farmId, Long batchId, LocalDate start, LocalDate end,
                                                       FinanceTransactionType type, boolean farmWide, String category,
                                                       String sourceType, FinanceTransactionStatus status,
                                                       Set<Long> includedBatchIds, boolean includeGeneralFarmRecords) {
        LocalDate effectiveEnd = end == null ? LocalDate.now() : end;
        LocalDate effectiveStart;
        String scope;
        String batchName = null;
        List<FinancialTransaction> values;

        if (batchId != null) {
            Batch batch = batchService.requireBatch(batchId, farmId);
            effectiveStart = start == null ? batch.getHatchDate() : start;
            scope = "BATCH";
            batchName = batch.getName();
        } else {
            effectiveStart = start == null ? effectiveEnd.minusMonths(12) : start;
            scope = "FARM";
        }

        if (effectiveStart.isAfter(effectiveEnd)) {
            throw new BadRequestException("Start date cannot be after end date");
        }

        Specification<FinancialTransaction> specification = transactionSpecification(farmId, effectiveStart,
                effectiveEnd, batchId, farmWide, type, category, sourceType, status,
                includedBatchIds, includeGeneralFarmRecords);
        values = repository.findAll(specification, Sort.by(Sort.Order.asc("transactionDate"),
                Sort.Order.asc("createdAt"), Sort.Order.asc("id")));

        long spanDays = ChronoUnit.DAYS.between(effectiveStart, effectiveEnd) + 1;
        Map<LocalDate, BigDecimal> incomeByBucket = new LinkedHashMap<>();
        Map<LocalDate, BigDecimal> expenseByBucket = new LinkedHashMap<>();
        Map<String, BigDecimal> expenseByCategory = new LinkedHashMap<>();
        BigDecimal income = BigDecimal.ZERO;
        BigDecimal saleIncome = BigDecimal.ZERO;
        long saleTransactionCount = 0;
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
                if ("SALE_EVENT".equalsIgnoreCase(tx.getSourceType())) {
                    saleIncome = saleIncome.add(tx.getAmount());
                    saleTransactionCount++;
                }
                incomeByBucket.merge(bucket, tx.getAmount(), BigDecimal::add);
            } else {
                if (batchId != null && "INVENTORY_PURCHASE".equalsIgnoreCase(tx.getSourceType())) continue;
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
                ? "Farm view includes batch-linked and general farm records."
                : "Batch view includes only transactions linked to this batch; farm-wide costs are excluded.";
        BigDecimal productsConsumed = BigDecimal.ZERO;
        boolean productCostComplete = true;
        if (batchId != null && inventoryMovementRepository != null) {
            List<InventoryMovement> movements = inventoryMovementRepository.findByFarmIdAndBatchIdAndOccurredAtBetweenOrderByOccurredAtAsc(
                    farmId, batchId, effectiveStart.atStartOfDay(java.time.ZoneId.of("Asia/Manila")).toInstant(),
                    effectiveEnd.plusDays(1).atStartOfDay(java.time.ZoneId.of("Asia/Manila")).toInstant());
            for (InventoryMovement movement : movements) {
                if (movement.getMovementType() != InventoryMovementType.USAGE) continue;
                if (movement.getInventoryValueDelta() == null) productCostComplete = false;
                else productsConsumed = productsConsumed.add(movement.getInventoryValueDelta().abs());
            }
        }
        BigDecimal totalRecordedBatchCost = expense.add(productsConsumed);
        return new FinanceAnalyticsResponse(scope, batchId, batchName, effectiveStart, effectiveEnd, currency,
                new FinanceAnalyticsResponse.Totals(income, saleIncome, income.subtract(saleIncome), saleTransactionCount,
                        expense, income.subtract(expense), productsConsumed, totalRecordedBatchCost,
                        income.subtract(totalRecordedBatchCost), productCostComplete, postedCount, voidedCount),
                series, categoryTotals, new FinanceAnalyticsResponse.Limitations(batchId != null, limitation));
    }

    private Specification<FinancialTransaction> transactionSpecification(Long farmId, LocalDate start, LocalDate end,
                                                                          Long batchId, Boolean farmWide,
                                                                          FinanceTransactionType type, String category,
                                                                          String sourceType, FinanceTransactionStatus status) {
        return transactionSpecification(farmId, start, end, batchId, farmWide, type, category, sourceType, status,
                null, false);
    }

    private Specification<FinancialTransaction> transactionSpecification(Long farmId, LocalDate start, LocalDate end,
                                                                          Long batchId, Boolean farmWide,
                                                                          FinanceTransactionType type, String category,
                                                                          String sourceType, FinanceTransactionStatus status,
                                                                          Set<Long> includedBatchIds,
                                                                          boolean includeGeneralFarmRecords) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("farmId"), farmId));
            if (start != null) predicates.add(cb.greaterThanOrEqualTo(root.get("transactionDate"), start));
            if (end != null) predicates.add(cb.lessThanOrEqualTo(root.get("transactionDate"), end));
            if (includedBatchIds != null) {
                List<Predicate> scopePredicates = new ArrayList<>();
                if (!includedBatchIds.isEmpty()) scopePredicates.add(root.get("batchId").in(includedBatchIds));
                if (includeGeneralFarmRecords) scopePredicates.add(cb.isNull(root.get("batchId")));
                predicates.add(scopePredicates.isEmpty()
                        ? cb.disjunction()
                        : cb.or(scopePredicates.toArray(Predicate[]::new)));
            } else {
                if (batchId != null) predicates.add(cb.equal(root.get("batchId"), batchId));
                if (Boolean.TRUE.equals(farmWide)) predicates.add(cb.isNull(root.get("batchId")));
            }
            if (type != null) predicates.add(cb.equal(root.get("type"), type));
            if (status != null) predicates.add(cb.equal(root.get("status"), status));
            if (category != null && !category.isBlank()) predicates.add(cb.equal(root.get("category"), category.trim()));
            if (sourceType != null && !sourceType.isBlank()) predicates.add(cb.equal(root.get("sourceType"), sourceType.trim()));
            return cb.and(predicates.toArray(Predicate[]::new));
        };
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
        if (batchId != null) batchService.requireWritableBatch(batchId, farmId);
        if (cycleId != null) incubationService.require(cycleId, farmId);
    }
    private String trim(String value) { return value == null || value.isBlank() ? null : value.trim(); }
}
