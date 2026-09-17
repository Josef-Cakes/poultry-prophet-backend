package com.poultryprophet.analytics.dto;

import com.poultryprophet.analytics.Indicator;
import com.poultryprophet.analytics.ThresholdConfig;
import com.poultryprophet.record.ObservationQuality;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public record IndicatorResponse(
        Long id,
        Long batchId,
        Long recordId,
        LocalDate recordDate,
        Double bhi,
        Double bsi,
        Double wfr,
        Double readinessScore,
        Double temperatureC,
        Integer mortalityCount,
        Double feedIntakeG,
        Double waterIntakeMl,
        Double temperatureScore,
        Double mortalityScore,
        Double feedScore,
        Double waterScore,
        Double temperatureContribution,
        Double mortalityContribution,
        Double feedContribution,
        Double waterContribution,
        ObservationQuality temperatureQuality,
        ObservationQuality feedQuality,
        ObservationQuality waterQuality,
        String formulaVersion,
        boolean sufficientData,
        String missingDataWarning,
        Map<String, MetricExplanation> metrics,
        List<FactorContribution> factors,
        Instant computedAt
) {
    /** Must be invoked inside an open persistence context (lazy record/batch access). */
    public static IndicatorResponse from(Indicator i) {
        return from(i, Map.of());
    }

    public static IndicatorResponse from(Indicator i, Map<String, ThresholdConfig> thresholds) {
        return new IndicatorResponse(
                i.getId(),
                i.getBatch().getId(),
                i.getRecord().getId(),
                i.getRecord().getRecordDate(),
                i.getBhi(),
                i.getBsi(),
                i.getWfr(),
                i.getReadinessScore(),
                i.getTemperatureC(),
                i.getMortalityCount(),
                i.getFeedIntakeG(),
                i.getWaterIntakeMl(),
                i.getTemperatureScore(),
                i.getMortalityScore(),
                i.getFeedScore(),
                i.getWaterScore(),
                i.getTemperatureContribution(),
                i.getMortalityContribution(),
                i.getFeedContribution(),
                i.getWaterContribution(),
                i.getTemperatureQuality(),
                i.getFeedQuality(),
                i.getWaterQuality(),
                i.getFormulaVersion(),
                i.isSufficientData(),
                i.getMissingDataWarning(),
                metricExplanations(i, thresholds),
                List.of(
                        new FactorContribution("temperature", "Temperature", i.getTemperatureC(), "°C",
                                i.getTemperatureScore(), i.getTemperatureContribution()),
                        new FactorContribution("mortality", "Health deaths",
                                i.getMortalityCount() == null ? null : i.getMortalityCount().doubleValue(), "birds",
                                i.getMortalityScore(), i.getMortalityContribution()),
                        new FactorContribution("feed", "Feed intake", i.getFeedIntakeG(), "g/day",
                                i.getFeedScore(), i.getFeedContribution()),
                        new FactorContribution("water", "Water intake", i.getWaterIntakeMl(), "mL/day",
                                i.getWaterScore(), i.getWaterContribution())),
                i.getComputedAt());
    }

    private static Map<String, MetricExplanation> metricExplanations(
            Indicator i, Map<String, ThresholdConfig> thresholds) {
        return Map.of(
                "BHI", metric(i.getBhi(), "score (0-100)", thresholds.get("BHI")),
                "BSI", i.getBsi() == null
                        ? new MetricExplanation(null, "score (0-100)", null, null, "NOT_APPLICABLE")
                        : metric(i.getBsi(), "score (0-100)", thresholds.get("BSI")),
                "WFR", metric(i.getWfr(), "mL/g", thresholds.get("WFR")));
    }

    private static MetricExplanation metric(Double value, String unit, ThresholdConfig threshold) {
        String status;
        if (value == null) {
            status = "INSUFFICIENT_DATA";
        } else if (threshold == null) {
            status = "NO_CONFIGURED_RANGE";
        } else if (value >= threshold.getMinValue() && value <= threshold.getMaxValue()) {
            status = "WITHIN_CONFIGURED_RANGE";
        } else {
            status = "OUTSIDE_CONFIGURED_RANGE";
        }
        return new MetricExplanation(value, unit,
                threshold == null ? null : threshold.getMinValue(),
                threshold == null ? null : threshold.getMaxValue(), status);
    }
}
