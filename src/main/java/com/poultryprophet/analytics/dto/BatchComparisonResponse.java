package com.poultryprophet.analytics.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** Neutral, common-window batch comparison. It deliberately contains no rank or score. */
public record BatchComparisonResponse(
        int requestedWindowDays,
        int effectiveWindowDays,
        String timeZone,
        List<String> warnings,
        List<BatchComparisonRow> batches
) {
    public record BatchComparisonRow(
            Long batchId,
            String batchName,
            String bloodline,
            String source,
            LocalDate windowStart,
            LocalDate windowEnd,
            int initialPopulation,
            Integer populationAtWindowEnd,
            String populationStatus,
            String populationWarning,
            LocalDate firstInvalidEventDate,
            long healthRelatedDeaths,
            Double healthRelatedLossRatePercent,
            long healthConcerns,
            long interventionRecords,
            Map<String, Long> populationChangesByCause,
            Integer evaluatedAtSelection,
            Integer acceptedAtSelection,
            Double selectionRatePercent,
            String selectionDataStatus,
            int sourceEventCount,
            int sourceProductRecordCount,
            List<String> limitations
    ) {}
}
