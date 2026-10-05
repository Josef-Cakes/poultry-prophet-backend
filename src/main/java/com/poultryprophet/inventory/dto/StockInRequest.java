package com.poultryprophet.inventory.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record StockInRequest(
        @NotNull @DecimalMin(value = "0.001") BigDecimal quantity,
        LocalDate stockDate,
        BigDecimal totalCost,
        Long batchId,
        String supplier,
        Boolean recordExpense,
        String notes,
        UUID operationId
) {}
