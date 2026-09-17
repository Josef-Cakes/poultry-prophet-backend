package com.poultryprophet.analytics.dto;

/** Threshold-aware status for one displayed indicator. */
public record MetricExplanation(
        Double value,
        String unit,
        Double configuredMin,
        Double configuredMax,
        String status
) {
}
