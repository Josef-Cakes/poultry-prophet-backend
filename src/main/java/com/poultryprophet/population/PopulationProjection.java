package com.poultryprophet.population;

import java.time.LocalDate;

/**
 * Read-only result of replaying a batch population ledger.
 *
 * <p>The raw value is intentionally retained for reconciliation.  Callers that need to display
 * a population must use {@link #validPopulation()}, or the bounded value only when they are
 * explicitly presenting a safe legacy fallback.  A malformed ledger must never be rendered as
 * if it were a real population.</p>
 */
public record PopulationProjection(
        int initialPopulation,
        long calculatedPopulation,
        Integer validPopulation,
        int boundedPopulation,
        int sourceEventCount,
        boolean reconciliationRequired,
        String status,
        String issueCode,
        Long firstInvalidEventId,
        LocalDate firstInvalidEventDate,
        String reconciliationMessage
) {
    public static final String VALID = "VALID";
    public static final String RECONCILIATION_REQUIRED = "RECONCILIATION_REQUIRED";
}
