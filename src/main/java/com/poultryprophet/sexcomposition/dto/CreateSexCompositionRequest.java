package com.poultryprophet.sexcomposition.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.UUID;

public record CreateSexCompositionRequest(
        @NotNull LocalDate observedOn,
        @NotNull @Min(0) Integer maleCount,
        @NotNull @Min(0) Integer femaleCount,
        @NotNull @Min(0) Integer unclassifiedCount,
        String revisionReason,
        String notes,
        UUID operationId
) {}
