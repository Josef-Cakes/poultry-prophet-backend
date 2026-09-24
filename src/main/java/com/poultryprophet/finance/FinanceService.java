package com.poultryprophet.finance;

import com.poultryprophet.batch.BatchService;
import com.poultryprophet.common.BadRequestException;
import com.poultryprophet.common.NotFoundException;
import com.poultryprophet.finance.dto.*;
import com.poultryprophet.incubation.IncubationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

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

    private void validateParent(Long farmId, Long batchId, Long cycleId) {
        if (batchId != null) batchService.requireBatch(batchId, farmId);
        if (cycleId != null) incubationService.require(cycleId, farmId);
    }
    private String trim(String value) { return value == null || value.isBlank() ? null : value.trim(); }
}
