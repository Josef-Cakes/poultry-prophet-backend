package com.poultryprophet.incubation;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class IncubationMetricsTest {
    @Test
    void calculatesACompletedCycleRateAndDuration() {
        IncubationCycle cycle = cycle(500, 400, 80, 20, IncubationStatus.COMPLETED);

        assertThat(IncubationMetrics.hatchRatePercent(cycle)).isEqualTo(80.0);
        assertThat(IncubationMetrics.durationDays(cycle)).isEqualTo(21L);
    }

    @Test
    void roundsRepeatingRatesToOneDecimal() {
        IncubationCycle cycle = cycle(3, 1, 1, 1, IncubationStatus.COMPLETED);

        assertThat(IncubationMetrics.hatchRatePercent(cycle)).isEqualTo(33.3);
    }

    @Test
    void distinguishesZeroHatchFromAnUnfinishedResult() {
        IncubationCycle zeroHatch = cycle(10, 0, 10, 0, IncubationStatus.COMPLETED);
        IncubationCycle unfinished = cycle(10, null, null, null, IncubationStatus.INCUBATING);
        IncubationCycle cancelled = cycle(10, 0, 10, 0, IncubationStatus.CANCELLED);

        assertThat(IncubationMetrics.hatchRatePercent(zeroHatch)).isEqualTo(0.0);
        assertThat(IncubationMetrics.hatchRatePercent(unfinished)).isNull();
        assertThat(IncubationMetrics.hatchRatePercent(cancelled)).isNull();
    }

    @Test
    void excludesCompletedRecordsWithNegativeOrUnreconciledCounts() {
        IncubationCycle negative = cycle(10, -1, 10, 1, IncubationStatus.COMPLETED);
        IncubationCycle unreconciled = cycle(10, 5, 5, 1, IncubationStatus.COMPLETED);

        assertThat(IncubationMetrics.hatchRatePercent(negative)).isNull();
        assertThat(IncubationMetrics.hatchRatePercent(unreconciled)).isNull();
        assertThat(IncubationMetrics.aggregate(List.of(negative, unreconciled)).eggsLoaded()).isZero();
    }

    @Test
    void usesAWeightedRateAndExcludesUnfinishedCycles() {
        IncubationCycle first = cycle(500, 400, 80, 20, IncubationStatus.COMPLETED);
        IncubationCycle second = cycle(3, 1, 1, 1, IncubationStatus.COMPLETED);
        IncubationCycle unfinished = cycle(1000, null, null, null, IncubationStatus.INCUBATING);

        IncubationMetrics.Aggregate result = IncubationMetrics.aggregate(List.of(first, second, unfinished));

        assertThat(result.eggsLoaded()).isEqualTo(503);
        assertThat(result.hatched()).isEqualTo(401);
        assertThat(result.hatchRatePercent()).isEqualTo(79.7);
    }

    private static IncubationCycle cycle(int eggs, Integer hatched, Integer unhatched,
                                         Integer removed, IncubationStatus status) {
        IncubationCycle cycle = new IncubationCycle();
        cycle.setEggsLoaded(eggs);
        cycle.setLoadedDate(LocalDate.of(2026, 9, 1));
        cycle.setActualHatchDate(status == IncubationStatus.COMPLETED ? LocalDate.of(2026, 9, 22) : null);
        cycle.setHatchedCount(hatched);
        cycle.setUnhatchedCount(unhatched);
        cycle.setRemovedDamagedCount(removed);
        cycle.setStatus(status);
        return cycle;
    }
}
