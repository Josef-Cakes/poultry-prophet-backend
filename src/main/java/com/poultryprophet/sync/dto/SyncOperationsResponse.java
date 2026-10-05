package com.poultryprophet.sync.dto;

import java.util.List;

public record SyncOperationsResponse(
        int received,
        int applied,
        int conflicts,
        int rejected,
        int retryable,
        List<SyncOperationResult> results
) {
}
