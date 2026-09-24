package com.poultryprophet.analytics.dto;

/** Explainable BHI input, normalized score, and weighted contribution. */
public record FactorContribution(
        String key,
        String label,
        Double rawValue,
        String unit,
        Double score,
        Double contribution
) {
}
