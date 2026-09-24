package com.poultryprophet.finance.dto;

import com.poultryprophet.finance.FinancialTransaction;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record FinancialTransactionResponse(
        Long id, Long farmId, Long batchId, Long incubationCycleId, LocalDate transactionDate,
        String type, String category, BigDecimal amount, String currency, String counterparty,
        String description, String status, Long enteredBy, String voidReason, Instant createdAt
) {
    public static FinancialTransactionResponse from(FinancialTransaction tx) {
        return new FinancialTransactionResponse(tx.getId(), tx.getFarmId(), tx.getBatchId(), tx.getIncubationCycleId(),
                tx.getTransactionDate(), tx.getType().name(), tx.getCategory(), tx.getAmount(), tx.getCurrency(),
                tx.getCounterparty(), tx.getDescription(), tx.getStatus().name(), tx.getEnteredBy(),
                tx.getVoidReason(), tx.getCreatedAt());
    }
}
