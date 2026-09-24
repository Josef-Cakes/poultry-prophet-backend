package com.poultryprophet.analytics;

/** Immutable output of an analytics computation pass (SDD 2.1/2.2). */
public record IndicatorResult(
        Double bhi,
        Double bsi,
        Double wfr,
        Double readinessScore,
        double temperatureScore,
        double mortalityScore,
        Double feedScore,
        Double waterScore,
        Double temperatureContribution,
        Double mortalityContribution,
        Double feedContribution,
        Double waterContribution,
        boolean sufficientData,
        String missingDataWarning,
        String formulaVersion
) {
}
