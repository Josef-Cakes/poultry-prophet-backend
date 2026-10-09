package com.poultryprophet.input.dto;

import com.poultryprophet.input.InputProductType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.math.BigDecimal;
import java.util.UUID;

public record CreateFarmInputRequest(
        Long batchId,
        Long incubationCycleId,
        Instant recordedAt,
        @NotNull InputProductType productType,
        @NotBlank String brandName,
        String productName,
        @DecimalMin(value = "0.0", inclusive = false) BigDecimal quantity,
        String unit,
        String route,
        String purpose,
        String notes,
        UUID operationId,
        Long farmProductId,
        Integer affectedBirdCount
) {
    /** Compatibility constructor for existing online callers and test fixtures. */
    public CreateFarmInputRequest(Long batchId,
                                  Long incubationCycleId,
                                  Instant recordedAt,
                                  InputProductType productType,
                                  String brandName,
                                  String productName,
                                  BigDecimal quantity,
                                  String unit,
                                  String route,
                                  String purpose,
                                  String notes) {
        this(batchId, incubationCycleId, recordedAt, productType, brandName, productName,
                quantity, unit, route, purpose, notes, null, null, null);
    }
}
