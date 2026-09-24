package com.poultryprophet.selectionreview;

import java.time.LocalDate;

public record CreateSelectionReviewRequest(
        LocalDate periodStart,
        LocalDate periodEnd,
        LocalDate asOfDate,
        String purpose,
        String snapshotNote,
        String idempotencyKey
) {}
