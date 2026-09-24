package com.poultryprophet.batch.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.List;

public record CreateBatchRequest(
        @NotBlank String name,
        @NotNull @Min(1) Integer initialPopulation,
        @NotNull LocalDate startDate,
        // Stage is now auto-derived from the start/hatch date, so this is optional and ignored.
        // Retained for backwards compatibility with older clients.
        Long stageId,
        // Blueprint 5.2/5.3: descriptive metadata. Bloodline is collected but NOT scored.
        String bloodline,
        String source,
        List<Long> handlerUserIds
) {
}
