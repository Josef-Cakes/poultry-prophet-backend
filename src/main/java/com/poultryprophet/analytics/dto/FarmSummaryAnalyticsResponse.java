package com.poultryprophet.analytics.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** Manager-facing descriptive farm summary; it does not expose unsupported scores. */
public record FarmSummaryAnalyticsResponse(
        String scope,
        Long batchId,
        String batchName,
        LocalDate startDate,
        LocalDate endDate,
        String timeZone,
        Instant generatedAt,
        PopulationSummary population,
        EventSummary events,
        List<ActivityBucket> activity,
        FinanceSummary finance,
        TaskSummary tasks,
        IncubationSummary incubation,
        Map<String, Long> inputTypes,
        List<String> limitations
) {
    public record PopulationSummary(long activeBatches, long initialPopulation,
                                    long currentPopulation, boolean available) {}
    public record EventSummary(long healthRelatedDeaths, long otherLosses,
                               long healthConcerns, long interventions,
                               long totalEvents, long birdsAffected) {}
    /** All activity values use event records as their unit. */
    public record ActivityBucket(LocalDate periodStart, LocalDate periodEnd,
                                 String label, String unit, long healthConcerns,
                                 long healthRelatedDeaths, long otherLosses,
                                 long interventions, long otherEvents) {}
    public record FinanceSummary(String currency, String income, String expense,
                                 String net, long transactionCount,
                                 List<CashFlowBucket> series,
                                 List<CategoryTotal> categories,
                                 boolean available, String limitation) {}
    public record CashFlowBucket(LocalDate periodStart, LocalDate periodEnd,
                                 String label, String income, String expense,
                                 String net) {}
    public record CategoryTotal(String category, String amount) {}
    public record TaskSummary(long total, long open, long completed, long overdue,
                              double completionRatePercent, boolean available) {}
    public record IncubationSummary(long totalCycles, long completedCycles,
                                    long inProgressCycles, int finalizedEggsLoaded,
                                    int finalizedHatched, double finalizedHatchRatePercent,
                                    double averageDurationDays, boolean available) {}
}
