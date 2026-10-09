package com.poultryprophet.inventory.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record StockInRequest(
        @NotNull @DecimalMin(value = "0.001") BigDecimal quantity,
        LocalDate stockDate,
        BigDecimal purchaseUnitCost,
        Long batchId,
        String supplier,
        // Legacy field retained temporarily; the server derives expenses automatically.
        Boolean recordExpense,
        Boolean freeOfCharge,
        String notes,
        UUID operationId,
        // Legacy field retained temporarily for older clients.
        BigDecimal totalCost
) {
    /** Compatibility constructor for older clients that supplied a total purchase cost. */
    public StockInRequest(BigDecimal quantity, LocalDate stockDate, BigDecimal totalCost,
                          Long batchId, String supplier, Boolean recordExpense,
                          Boolean freeOfCharge, String notes, UUID operationId) {
        this(quantity, stockDate, null, batchId, supplier, recordExpense, freeOfCharge, notes, operationId, totalCost);
    }
}
