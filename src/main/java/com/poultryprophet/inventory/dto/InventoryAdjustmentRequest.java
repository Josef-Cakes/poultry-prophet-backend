package com.poultryprophet.inventory.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record InventoryAdjustmentRequest(
        @NotNull BigDecimal quantityDelta,
        @NotBlank String reason,
        LocalDate occurredOn,
        UUID operationId
) {}
