package com.poultryprophet.sync.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.UUID;

/** One typed, idempotent operation from a device outbox. */
public record SyncOperationRequest(
        @NotNull UUID operationId,
        @Min(1) int schemaVersion,
        @NotBlank String entityType,
        @NotNull Long batchId,
        @NotNull Instant occurredAt,
        @NotNull JsonNode payload
) {
}
