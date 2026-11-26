package com.poultryprophet.finance.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record CorrectExpenseRequest(@NotNull LocalDate transactionDate, @NotBlank String category,
                                    @NotNull @DecimalMin("0.01") @jakarta.validation.constraints.Digits(integer=12, fraction=2) BigDecimal amount,
                                    @NotBlank String reason, @NotNull Instant expectedUpdatedAt,
                                    @NotNull UUID operationId) {}
