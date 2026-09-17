package com.poultryprophet.sync.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.time.LocalDate;

/** One buffered offline entry. {@code clientId} echoes back so the PWA can match results. */
public record SyncItemRequest(
        @NotNull String clientId,
        @NotNull Long batchId,
        @NotNull LocalDate recordDate,
        @NotNull Double temperatureC,
        @Min(0) Integer mortalityCount,
        @NotNull Double feedIntakeG,
        @NotNull Double waterIntakeMl,
        String behaviorNotes,
        @NotNull Instant updatedAt
) {
}
