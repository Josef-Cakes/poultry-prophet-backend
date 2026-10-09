package com.poultryprophet.sexcomposition;

import java.time.LocalDate;

/** Read-side effective sex composition derived from a manual baseline and later attributed events. */
public record SexCompositionProjection(
        int maleCount,
        int femaleCount,
        int unclassifiedCount,
        LocalDate baselineObservedOn,
        LocalDate projectedAsOf,
        Long baselineId,
        Long baselineEventId,
        String status,
        String message
) {
    public static final String VALID = "VALID";
    public static final String NO_BASELINE = "NO_BASELINE";
    public static final String MISSING_ALLOCATION = "MISSING_ALLOCATION";
    public static final String NEGATIVE_CATEGORY = "NEGATIVE_CATEGORY";

    public int total() {
        return maleCount + femaleCount + unclassifiedCount;
    }

    public boolean hasBaseline() {
        return baselineId != null;
    }

    public boolean valid() {
        return VALID.equals(status);
    }
}
