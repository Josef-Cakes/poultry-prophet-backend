package com.poultryprophet.finance.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Batch- or farm-scoped recorded cash-flow data for the manager dashboard. */
public record FinanceAnalyticsResponse(
        String scope,
        Long batchId,
        String batchName,
        LocalDate startDate,
        LocalDate endDate,
        String currency,
        Totals totals,
        List<SeriesPoint> series,
        List<CategoryTotal> categoryTotals,
        Limitations limitations
) {
    public record Totals(
            BigDecimal recordedIncome,
            BigDecimal recordedExpense,
            BigDecimal recordedNetCashFlow,
            BigDecimal productsConsumedCost,
            BigDecimal totalRecordedBatchCost,
            BigDecimal recordedContribution,
            boolean productCostComplete,
            long postedTransactionCount,
            long voidedTransactionCount
    ) {}

    public record SeriesPoint(
            LocalDate periodStart,
            LocalDate periodEnd,
            String label,
            BigDecimal income,
            BigDecimal expense,
            BigDecimal netChange,
            BigDecimal cumulativeNetCashFlow
    ) {}

    public record CategoryTotal(String category, BigDecimal amount) {}

    public record Limitations(boolean farmWideCostsExcluded, String message) {}
}
