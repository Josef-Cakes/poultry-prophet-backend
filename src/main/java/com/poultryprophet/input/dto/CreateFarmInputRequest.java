package com.poultryprophet.input.dto;

import com.poultryprophet.input.InputProductType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

public record CreateFarmInputRequest(
        Long batchId,
        Long incubationCycleId,
        Instant recordedAt,
        @NotNull InputProductType productType,
        @NotBlank String brandName,
        String productName,
        @DecimalMin(value = "0.0", inclusive = false) Double quantity,
        String unit,
        String route,
        String purpose,
        String notes
) {
}
