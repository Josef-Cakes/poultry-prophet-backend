package com.poultryprophet.finance.dto;

import com.poultryprophet.finance.FinancialTransaction;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record FinancialTransactionResponse(
        Long id, Long farmId, Long batchId, Long incubationCycleId, LocalDate transactionDate,
        String type, String category, BigDecimal amount, String currency, String counterparty,
        String description, String sourceType, Long sourceEventId, String sourceOperationId, String status, Long enteredBy, String voidReason, Instant createdAt
) {
    /** Compatibility constructor for existing focused tests and integrations. */
    public FinancialTransactionResponse(Long id, Long farmId, Long batchId, Long incubationCycleId, LocalDate transactionDate,
                                        String type, String category, BigDecimal amount, String currency, String counterparty,
                                        String description, String sourceType, String sourceOperationId, String status,
                                        Long enteredBy, String voidReason, Instant createdAt) {
        this(id, farmId, batchId, incubationCycleId, transactionDate, type, category, amount, currency, counterparty,
                description, sourceType, null, sourceOperationId, status, enteredBy, voidReason, createdAt);
    }

    public static FinancialTransactionResponse from(FinancialTransaction tx) {
        return new FinancialTransactionResponse(tx.getId(), tx.getFarmId(), tx.getBatchId(), tx.getIncubationCycleId(),
                tx.getTransactionDate(), tx.getType().name(), tx.getCategory(), tx.getAmount(), tx.getCurrency(),
                tx.getCounterparty(), tx.getDescription(), tx.getSourceType(), tx.getSourceEventId(), tx.getSourceOperationId(), tx.getStatus().name(), tx.getEnteredBy(),
                tx.getVoidReason(), tx.getCreatedAt());
    }
}
