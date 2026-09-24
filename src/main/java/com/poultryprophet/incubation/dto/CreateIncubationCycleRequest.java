package com.poultryprophet.incubation.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record CreateIncubationCycleRequest(
        @NotBlank String cycleName,
        @NotBlank String incubatorCode,
        String eggSource,
        String bloodline,
        @NotNull LocalDate loadedDate,
        @NotNull @Min(1) Integer eggsLoaded,
        LocalDate expectedHatchDate,
        String notes
) {
}
