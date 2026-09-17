package com.poultryprophet.analytics;

import com.poultryprophet.batch.Batch;
import com.poultryprophet.config.AnalyticsProperties;
import com.poultryprophet.record.DailyRecord;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * SDD 2.1/2.2: computes the Batch Health Index (BHI), Brooder Stress Index (BSI) and
 * Water-to-Feed Ratio (WFR). The formulas below are PROVISIONAL (see SDD preface) and are
 * weight/threshold-configurable via {@link AnalyticsProperties}; they may change once the
 * SRS is approved. Methods are deterministic given their inputs (no I/O).
 */
@Service
public class AnalyticsService {

    private static final double BROODING_TARGET_TEMP_C = 33.0;
    public static final String FORMULA_VERSION = "RULE_BASED_BHI_BSI_WFR_V2";

    private final AnalyticsProperties props;

    public AnalyticsService(AnalyticsProperties props) {
        this.props = props;
    }

    /**
     * Computes all indicators for the most recent record in {@code recentDesc} (index 0 is
     * the newest). Returns null if there are no records.
     */
    public IndicatorResult compute(List<DailyRecord> recentDesc, Batch batch) {
        if (recentDesc == null || recentDesc.isEmpty()) {
            return null;
        }
        DailyRecord latest = recentDesc.get(0);
        double target = props.targetTempFor(batch.getStage().getName());
        double tempScore = round1(clamp(100.0 - Math.abs(latest.getTemperatureC() - target) * 8.0));
        double mortalityScore = mortalityScore(latest, batch);
        Double feedScore = deviationScore(latest.getFeedIntakeG(), averageFeedExcludingLatest(recentDesc));
        Double waterScore = deviationScore(latest.getWaterIntakeMl(), averageWaterExcludingLatest(recentDesc));
        boolean sufficientData = feedScore != null && waterScore != null;
        Double bhi = sufficientData ? weightedScore(tempScore, mortalityScore, feedScore, waterScore) : null;
        Double wfr = computeWfr(latest);
        Double bsi = batch.isBrooding() ? computeBsi(recentDesc, batch) : null;
        AnalyticsProperties.Weights w = props.getWeights();
        double totalWeight = w.total() == 0 ? 1.0 : w.total();
        return new IndicatorResult(
                bhi,
                bsi,
                wfr,
                null,
                tempScore,
                mortalityScore,
                feedScore,
                waterScore,
                sufficientData ? contribution(tempScore, w.getTemperature(), totalWeight) : null,
                sufficientData ? contribution(mortalityScore, w.getMortality(), totalWeight) : null,
                sufficientData ? contribution(feedScore, w.getFeed(), totalWeight) : null,
                sufficientData ? contribution(waterScore, w.getWater(), totalWeight) : null,
                sufficientData,
                sufficientData ? null : "BHI requires at least one prior positive feed and water observation.",
                FORMULA_VERSION);
    }

    /** Weighted composite of normalised temperature, mortality, feed and water sub-scores (0-100). */
    public double computeBhi(List<DailyRecord> recentDesc, Batch batch) {
        DailyRecord latest = recentDesc.get(0);

        double target = props.targetTempFor(batch.getStage().getName());
        double tempScore = clamp(100.0 - Math.abs(latest.getTemperatureC() - target) * 8.0);

        double population = Math.max(1.0, batch.getCurrentPopulation());
        double dailyMortalityRate = latest.getMortalityCount() / population;
        double mortalityScore = clamp(100.0 - dailyMortalityRate * 10_000.0);

        Double feedScore = deviationScore(latest.getFeedIntakeG(), averageFeedExcludingLatest(recentDesc));
        Double waterScore = deviationScore(latest.getWaterIntakeMl(), averageWaterExcludingLatest(recentDesc));
        if (feedScore == null || waterScore == null) {
            return 0.0;
        }

        AnalyticsProperties.Weights w = props.getWeights();
        return weightedScore(tempScore, mortalityScore, feedScore, waterScore);
    }

    /** Brooding-stage stress (0-100, higher = more stress). Provisional heuristic. */
    public double computeBsi(List<DailyRecord> recentDesc, Batch batch) {
        DailyRecord latest = recentDesc.get(0);
        double tempPenalty = Math.abs(latest.getTemperatureC() - BROODING_TARGET_TEMP_C) * 6.0;
        double population = Math.max(1.0, batch.getCurrentPopulation());
        double mortalityPenalty = (latest.getMortalityCount() / population) * 8_000.0;
        double behaviorPenalty = hasStressSignals(latest.getBehaviorNotes()) ? 15.0 : 0.0;
        return round1(clamp(tempPenalty + mortalityPenalty + behaviorPenalty));
    }

    /** Water-to-feed ratio for the latest day; null (flagged) when feed intake is zero. */
    public Double computeWfr(DailyRecord latest) {
        if (latest.getFeedIntakeG() <= 0.0) {
            return null;
        }
        return round2(latest.getWaterIntakeMl() / latest.getFeedIntakeG());
    }

    /** Provisional readiness proxy. Tracks BHI until a dedicated formula is approved. */
    public double computeReadiness(double bhi) {
        return round1(bhi);
    }

    private double averageFeedExcludingLatest(List<DailyRecord> recentDesc) {
        if (recentDesc.size() <= 1) {
            return 0.0;
        }
        double sum = 0.0;
        for (int i = 1; i < recentDesc.size(); i++) {
            sum += recentDesc.get(i).getFeedIntakeG();
        }
        return sum / (recentDesc.size() - 1);
    }

    private double averageWaterExcludingLatest(List<DailyRecord> recentDesc) {
        if (recentDesc.size() <= 1) {
            return 0.0;
        }
        double sum = 0.0;
        for (int i = 1; i < recentDesc.size(); i++) {
            sum += recentDesc.get(i).getWaterIntakeMl();
        }
        return sum / (recentDesc.size() - 1);
    }

    /** No history is insufficient data, never a fabricated perfect score. */
    private Double deviationScore(double current, double average) {
        if (average <= 0.0) {
            return null;
        }
        double ratio = Math.abs(current - average) / average;
        return clamp(100.0 - ratio * 100.0);
    }

    private double weightedScore(double temperatureScore, double mortalityScore,
                                 double feedScore, double waterScore) {
        AnalyticsProperties.Weights w = props.getWeights();
        double totalWeight = w.total() == 0 ? 1.0 : w.total();
        return round1(clamp((temperatureScore * w.getTemperature()
                + mortalityScore * w.getMortality()
                + feedScore * w.getFeed()
                + waterScore * w.getWater()) / totalWeight));
    }

    private double contribution(double score, double weight, double totalWeight) {
        return round1(score * weight / totalWeight);
    }

    private double mortalityScore(DailyRecord latest, Batch batch) {
        double population = Math.max(1.0, batch.getCurrentPopulation());
        return round1(clamp(100.0 - (latest.getMortalityCount() / population) * 10_000.0));
    }

    private boolean hasStressSignals(String notes) {
        if (notes == null) {
            return false;
        }
        String lower = notes.toLowerCase();
        return lower.contains("pile") || lower.contains("huddle") || lower.contains("pant")
                || lower.contains("gasp") || lower.contains("lethargic");
    }

    private double clamp(double value) {
        return Math.max(0.0, Math.min(100.0, value));
    }

    private double round1(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    private double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
