package com.poultryprophet.finance.dto;

import com.poultryprophet.finance.FinanceTransactionType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record CreateFinancialTransactionRequest(
        Long batchId, Long incubationCycleId, @NotNull LocalDate transactionDate,
        @NotNull FinanceTransactionType type, @NotBlank String category,
        @NotNull @DecimalMin(value = "0.01") BigDecimal amount, String currency,
        String counterparty, String description, String sourceType, UUID sourceOperationId
) {
    public CreateFinancialTransactionRequest(Long batchId, Long incubationCycleId, LocalDate transactionDate,
                                             FinanceTransactionType type, String category, BigDecimal amount,
                                             String currency, String counterparty, String description) {
        this(batchId, incubationCycleId, transactionDate, type, category, amount, currency, counterparty,
                description, "MANUAL", null);
    }
}
