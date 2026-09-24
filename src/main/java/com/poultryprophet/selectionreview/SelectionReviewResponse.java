package com.poultryprophet.selectionreview;

import java.time.Instant;
import java.time.LocalDate;

public record SelectionReviewResponse(
        Long id,
        Long farmId,
        Long batchId,
        String batchName,
        LocalDate periodStart,
        LocalDate periodEnd,
        LocalDate asOfDate,
        SelectionReviewStatus status,
        ManagerReviewStatus reviewStatus,
        String managerNotes,
        LocalDate nextReviewDate,
        Integer versionNumber,
        String purpose,
        String snapshotNote,
        String payloadVersion,
        SelectionReviewPayload payload,
        Long generatedBy,
        Instant generatedAt,
        Instant sourceCutoffAt,
        boolean newerDataAvailable,
        Long reviewedBy,
        Instant reviewedAt
) {}
