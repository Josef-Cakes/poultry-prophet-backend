package com.poultryprophet.incubation.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record CompleteIncubationRequest(
        @NotNull LocalDate actualHatchDate,
        @NotNull @Min(0) Integer hatchedCount,
        @NotNull @Min(0) Integer unhatchedCount,
        @NotNull @Min(0) Integer removedDamagedCount,
        String notes
) {
}
