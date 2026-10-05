package com.poultryprophet.sync.dto;

import java.time.Instant;
import java.util.UUID;

public record SyncOperationResult(
        UUID operationId,
        String status,
        Long serverId,
        Instant serverTime,
        String message
) {
    public static SyncOperationResult applied(UUID id, Long serverId) {
        return new SyncOperationResult(id, "APPLIED", serverId, Instant.now(), null);
    }

    public static SyncOperationResult rejected(UUID id, String message) {
        return new SyncOperationResult(id, "REJECTED", null, Instant.now(), message);
    }

    public static SyncOperationResult conflict(UUID id, String message) {
        return new SyncOperationResult(id, "CONFLICT", null, Instant.now(), message);
    }

    public static SyncOperationResult retryable(UUID id, String message) {
        return new SyncOperationResult(id, "RETRYABLE", null, Instant.now(), message);
    }
}
