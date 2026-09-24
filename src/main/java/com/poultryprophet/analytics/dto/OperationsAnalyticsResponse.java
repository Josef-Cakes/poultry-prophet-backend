package com.poultryprophet.analytics.dto;

import java.time.LocalDate;

public record OperationsAnalyticsResponse(LocalDate startDate, LocalDate endDate,
                                          IncubationAnalyticsResponse incubation,
                                          TaskAnalyticsResponse tasks,
                                          InputUsageAnalyticsResponse inputs,
                                          FinanceSummary finance) {
    public record FinanceSummary(String currency, String income, String expense, String net, long transactionCount) {}
}
