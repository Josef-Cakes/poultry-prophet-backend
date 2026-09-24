package com.poultryprophet.finance.dto;

import com.poultryprophet.finance.FinanceTransactionType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;

public record CreateFinancialTransactionRequest(
        Long batchId, Long incubationCycleId, @NotNull LocalDate transactionDate,
        @NotNull FinanceTransactionType type, @NotBlank String category,
        @NotNull @DecimalMin(value = "0.01") BigDecimal amount, String currency,
        String counterparty, String description
) {}
