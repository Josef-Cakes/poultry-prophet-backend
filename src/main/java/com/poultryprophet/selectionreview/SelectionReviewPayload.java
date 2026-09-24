package com.poultryprophet.selectionreview;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Versioned, explainable report payload. Every displayed value is either derived from a named
 * source category or explicitly marked unavailable; no score or recommendation is generated.
 */
public record SelectionReviewPayload(
        String reportTitle,
        String disclaimer,
        String payloadVersion,
        LocalDate periodStart,
        LocalDate periodEnd,
        LocalDate asOfDate,
        BatchOverview batch,
        PopulationSummary population,
        List<HealthEventItem> healthEvents,
        List<ProductUseItem> productUse,
        IncubationSummary incubation,
        FinanceSummary finance,
        List<DataAvailabilityItem> dataAvailability,
        ReviewInstructions reviewInstructions
) {
    public record BatchOverview(
            Long batchId,
            String batchName,
            String bloodline,
            String source,
            int initialPopulation,
            int currentPopulation,
            String currentPopulationDisplay,
            long ageDays,
            String stageName,
            LocalDate startDate,
            LocalDate lastRecordedEventDate
    ) {}

    public record PopulationSummary(
            int initialPopulation,
            int currentPopulation,
            long healthRelatedDeaths,
            Double healthRelatedLossPercentage,
            long accidentalDeaths,
            long predation,
            long missing,
            long returned,
            long transfersOut,
            long transfersIn,
            long sales,
            long culling,
            long countCorrections,
            long legacyMortalityRecords,
            int sourceEventCount,
            Map<String, Long> categoryCounts
    ) {}

    public record HealthEventItem(
            Long sourceEventId,
            LocalDate eventDate,
            String eventType,
            String title,
            String severity,
            int affectedCount,
            String details,
            String tags,
            Long recordedBy
    ) {}

    public record ProductUseItem(
            Long sourceId,
            Instant recordedAt,
            String productType,
            String brandName,
            String productName,
            Double quantity,
            String unit,
            String purpose,
            String notes,
            Long recordedBy
    ) {}

    public record IncubationSummary(
            Long cycleId,
            String cycleName,
            String incubatorCode,
            LocalDate loadedDate,
            int eggsLoaded,
            LocalDate expectedHatchDate,
            LocalDate actualHatchDate,
            Integer hatchedCount,
            Integer unhatchedCount,
            Integer removedDamagedCount,
            Double hatchRate,
            String limitation
    ) {}

    public record FinanceSummary(
            LocalDate startDate,
            LocalDate endDate,
            BigDecimal recordedIncome,
            BigDecimal recordedExpense,
            BigDecimal recordedNetCashFlow,
            long postedTransactionCount,
            boolean recordsMayBeIncomplete,
            String limitation
    ) {}

    public record DataAvailabilityItem(
            String section,
            String status,
            int recordCount,
            LocalDate latestDate,
            String message
    ) {}

    public record ReviewInstructions(
            String status,
            String managerNotes,
            LocalDate nextReviewDate,
            String reviewedBy,
            Instant reviewedAt
    ) {}
}
