package com.poultryprophet.scoring;

import com.poultryprophet.batch.Batch;
import com.poultryprophet.batch.BatchService;
import com.poultryprophet.bird.Bird;
import com.poultryprophet.bird.BirdRepository;
import com.poultryprophet.record.DailyRecord;
import com.poultryprophet.record.DailyRecordRepository;
import com.poultryprophet.ranging.RangingRecord;
import com.poultryprophet.ranging.RangingRecordRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Blueprint section 6 - the keystone scoring engine. Computes four sub-scores and combines them
 * into one Conditioning Readiness Score (CRS) per bird:
 *
 * <pre>
 *   BHI (batch, inherited) = 0.50*mortality + 0.30*intake + 0.20*temperature
 *   Growth   (per bird)    = avg over weeks of clamp(100 - |%off-curve| * penalty)
 *   Health   (per bird)    = 100 - sum(deduction per logged event severity)
 *   Behaviour(per bird)    = avg of C/B+/A/A++ rating points
 *   CRS = 0.30*BHI + 0.30*Growth + 0.20*Health + 0.20*Behaviour
 * </pre>
 *
 * The engine is deterministic (same inputs -> same output), transparent (every sub-score traces
 * to inputs), and adjustable (all weights/thresholds live in {@link ScoringProperties}). Breed/
 * bloodline is deliberately NOT an input (Blueprint 5.3). Literature-backed thresholds and the
 * design-decision weights are both PROVISIONAL starting values.
 */
@Service
@ConditionalOnProperty(name = "app.features.legacy-individual-selection-enabled", havingValue = "true")
public class ScoringService {

    // Provisional brooding temperature safe band (deg C) used by the BHI temperature component.
    private static final double BROODING_TARGET_TEMP_C = 33.0;
    private static final double BROODING_TEMP_TOLERANCE_C = 3.0;

    private final BatchService batchService;
    private final BirdRepository birdRepository;
    private final DailyRecordRepository dailyRecordRepository;
    private final RangingRecordRepository rangingRecordRepository;
    private final BirdScoreRepository birdScoreRepository;
    private final ScoringProperties props;

    public ScoringService(BatchService batchService,
                          BirdRepository birdRepository,
                          DailyRecordRepository dailyRecordRepository,
                          RangingRecordRepository rangingRecordRepository,
                          BirdScoreRepository birdScoreRepository,
                          ScoringProperties props) {
        this.batchService = batchService;
        this.birdRepository = birdRepository;
        this.dailyRecordRepository = dailyRecordRepository;
        this.rangingRecordRepository = rangingRecordRepository;
        this.birdScoreRepository = birdScoreRepository;
        this.props = props;
    }

    /** Recomputes and persists scores for every bird in the batch; returns them ranked by CRS desc. */
    @Transactional
    public List<BirdScore> recomputeForBatch(Long batchId, Long farmId) {
        Batch batch = batchService.requireWritableBatch(batchId, farmId);
        double bhi = computeBhi(batch);
        for (Bird bird : birdRepository.findByBatchIdOrderByBandNumberAsc(batchId)) {
            scoreBird(bird, batch, bhi);
        }
        return birdScoreRepository.findByBatchIdOrderByCrsDesc(batchId);
    }

    private BirdScore scoreBird(Bird bird, Batch batch, double bhi) {
        List<RangingRecord> ranging = rangingRecordRepository.findByBirdIdOrderByRecordDateAsc(bird.getId());
        double growth = computeGrowth(ranging, batch);
        double health = computeHealthHistory(ranging);
        double behaviour = computeBehavioural(ranging);
        double crs = computeCrs(bhi, growth, health, behaviour);

        BirdScore score = birdScoreRepository.findByBirdId(bird.getId()).orElseGet(BirdScore::new);
        score.setBird(bird);
        score.setBatch(batch);
        score.setBroodingHealthIndex(bhi);
        score.setGrowthScore(growth);
        score.setHealthHistoryScore(health);
        score.setBehaviouralScore(behaviour);
        score.setCrs(crs);
        score.setComputedAt(Instant.now());
        return birdScoreRepository.save(score);
    }

    // --- Sub-scores (Blueprint 6.1) ---

    /**
     * Brooding Health Index for a batch (0-100), inherited by every bird in it. Combines a
     * mortality component (strongest literature signal), feed/water intake stability, and
     * temperature stability over the batch's brooding daily records.
     */
    public double computeBhi(Batch batch) {
        List<DailyRecord> records = dailyRecordRepository.findByBatchIdOrderByRecordDateAsc(batch.getId());
        ScoringProperties.BhiWeights w = props.getBhiWeights();
        double total = w.total() == 0 ? 1.0 : w.total();

        double mortalityScore = mortalityComponent(records, batch);
        double intakeScore = intakeComponent(records);
        double tempScore = temperatureComponent(records);

        double bhi = (mortalityScore * w.getMortality()
                + intakeScore * w.getIntake()
                + tempScore * w.getTemperature()) / total;
        return round1(clamp(bhi));
    }

    /** Cumulative brooding mortality mapped linearly: best% -> 100, worst% -> 0. */
    private double mortalityComponent(List<DailyRecord> records, Batch batch) {
        if (records.isEmpty()) {
            return 100.0;
        }
        int totalMortality = 0;
        for (DailyRecord r : records) {
            totalMortality += r.getMortalityCount();
        }
        double population = Math.max(1, batch.getInitialPopulation());
        double ratePct = (totalMortality / population) * 100.0;
        double best = props.getMortalityBestPct();
        double worst = props.getMortalityWorstPct();
        if (ratePct <= best) {
            return 100.0;
        }
        if (ratePct >= worst || worst <= best) {
            return ratePct >= worst ? 0.0 : 100.0;
        }
        return clamp(100.0 - ((ratePct - best) / (worst - best)) * 100.0);
    }

    /** Feed/water adequacy proxied by intake stability (low day-to-day variation = better). */
    private double intakeComponent(List<DailyRecord> records) {
        if (records.size() < 2) {
            return 100.0;
        }
        double feedStability = stabilityScore(records.stream().filter(r -> r.getFeedIntakeG() != null).mapToDouble(r -> r.getFeedIntakeG()).toArray());
        double waterStability = stabilityScore(records.stream().filter(r -> r.getWaterIntakeMl() != null).mapToDouble(r -> r.getWaterIntakeMl()).toArray());
        return clamp((feedStability + waterStability) / 2.0);
    }

    /** Share of brooding days whose temperature sat inside the safe band. */
    private double temperatureComponent(List<DailyRecord> records) {
        if (records.isEmpty()) {
            return 100.0;
        }
        long inBand = records.stream()
                .filter(r -> r.getTemperatureC() != null)
                .filter(r -> Math.abs(r.getTemperatureC() - BROODING_TARGET_TEMP_C) <= BROODING_TEMP_TOLERANCE_C)
                .count();
        return clamp((inBand / (double) records.size()) * 100.0);
    }

    /**
     * Growth trajectory (0-100): at each weekly milestone, penalise deviation from the expected
     * weight curve (both under and over). Averaged across milestones. No milestones -> 0 (the
     * bird has not been individually evaluated yet).
     */
    public double computeGrowth(List<RangingRecord> ranging, Batch batch) {
        if (ranging.isEmpty()) {
            return 0.0;
        }
        double sum = 0.0;
        int counted = 0;
        for (RangingRecord r : ranging) {
            double weeks = ChronoUnit.DAYS.between(batch.getStartDate(), r.getRecordDate()) / 7.0;
            double expected = props.expectedWeightForWeek(weeks);
            if (expected <= 0.0) {
                continue;
            }
            double pctOff = Math.abs(r.getWeightG() - expected) / expected * 100.0;
            sum += clamp(100.0 - pctOff * props.getGrowthPenaltyPerPct());
            counted++;
        }
        return counted == 0 ? 0.0 : round1(clamp(sum / counted));
    }

    /** Health history (0-100): starts at 100, subtracts per logged event by severity. */
    public double computeHealthHistory(List<RangingRecord> ranging) {
        double score = 100.0;
        for (RangingRecord r : ranging) {
            score -= props.deductionFor(r.getHealthEvent());
        }
        return round1(clamp(score));
    }

    /** Behavioural (0-100): average of the C/B+/A/A++ rating points. No ratings -> 0. */
    public double computeBehavioural(List<RangingRecord> ranging) {
        if (ranging.isEmpty()) {
            return 0.0;
        }
        double sum = 0.0;
        int counted = 0;
        for (RangingRecord r : ranging) {
            if (r.getQualityRating() != null) {
                sum += r.getQualityRating().getPoints();
                counted++;
            }
        }
        return counted == 0 ? 0.0 : round1(clamp(sum / counted));
    }

    /** Composite CRS (Blueprint 6.2). */
    public double computeCrs(double bhi, double growth, double health, double behaviour) {
        ScoringProperties.CrsWeights w = props.getCrsWeights();
        double total = w.total() == 0 ? 1.0 : w.total();
        double crs = (bhi * w.getBroodingHealth()
                + growth * w.getGrowth()
                + health * w.getHealthHistory()
                + behaviour * w.getBehavioural()) / total;
        return round1(clamp(crs));
    }

    /** 100 minus the coefficient of variation (as a percentage), clamped to 0-100. */
    private double stabilityScore(double[] values) {
        if (values.length == 0) {
            return 0.0;
        }
        double mean = 0.0;
        for (double v : values) {
            mean += v;
        }
        mean /= values.length;
        if (mean <= 0.0) {
            return 100.0;
        }
        double variance = 0.0;
        for (double v : values) {
            variance += Math.pow(v - mean, 2);
        }
        variance /= values.length;
        double cov = Math.sqrt(variance) / mean;
        return clamp(100.0 - cov * 100.0);
    }

    private double clamp(double value) {
        return Math.max(0.0, Math.min(100.0, value));
    }

    private double round1(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
}
