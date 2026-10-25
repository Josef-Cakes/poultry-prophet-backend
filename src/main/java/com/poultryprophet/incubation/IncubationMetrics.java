package com.poultryprophet.incubation;

import java.time.temporal.ChronoUnit;
import java.util.Collection;

/**
 * Authoritative derived metrics for incubation records.
 *
 * Hatch rate is the percentage of loaded eggs that hatched. It is intentionally
 * derived from the recorded counts instead of being stored in the database.
 */
public final class IncubationMetrics {
    private IncubationMetrics() {}

    public static Double hatchRatePercent(IncubationCycle cycle) {
        if (!hasFinalizedResult(cycle)) return null;
        return roundOne(cycle.getHatchedCount() * 100.0 / cycle.getEggsLoaded());
    }

    public static Long durationDays(IncubationCycle cycle) {
        if (cycle == null || cycle.getActualHatchDate() == null || cycle.getLoadedDate() == null) return null;
        long days = ChronoUnit.DAYS.between(cycle.getLoadedDate(), cycle.getActualHatchDate());
        return days < 0 ? null : days;
    }

    public static boolean hasFinalizedResult(IncubationCycle cycle) {
        return cycle != null
                && cycle.getStatus() == IncubationStatus.COMPLETED
                && cycle.getEggsLoaded() > 0
                && cycle.getHatchedCount() != null
                && cycle.getUnhatchedCount() != null
                && cycle.getRemovedDamagedCount() != null
                && cycle.getHatchedCount() >= 0
                && cycle.getUnhatchedCount() >= 0
                && cycle.getRemovedDamagedCount() >= 0
                && (long) cycle.getHatchedCount()
                + cycle.getUnhatchedCount()
                + cycle.getRemovedDamagedCount() == cycle.getEggsLoaded();
    }

    public static Aggregate aggregate(Collection<IncubationCycle> cycles) {
        long eggsLoaded = 0;
        long hatched = 0;
        long unhatched = 0;
        long removedDamaged = 0;
        long durationTotal = 0;
        long durationCount = 0;

        if (cycles != null) {
            for (IncubationCycle cycle : cycles) {
                if (!hasFinalizedResult(cycle)) continue;
                eggsLoaded += cycle.getEggsLoaded();
                hatched += cycle.getHatchedCount();
                unhatched += cycle.getUnhatchedCount();
                removedDamaged += cycle.getRemovedDamagedCount();
                Long duration = durationDays(cycle);
                if (duration != null) {
                    durationTotal += duration;
                    durationCount++;
                }
            }
        }

        Double hatchRate = eggsLoaded == 0 ? null : roundOne(hatched * 100.0 / eggsLoaded);
        Double averageDuration = durationCount == 0 ? null : roundOne(durationTotal * 1.0 / durationCount);
        return new Aggregate(Math.toIntExact(eggsLoaded), Math.toIntExact(hatched),
                Math.toIntExact(unhatched), Math.toIntExact(removedDamaged), hatchRate, averageDuration);
    }

    private static double roundOne(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    public record Aggregate(int eggsLoaded, int hatched, int unhatched, int removedDamaged,
                            Double hatchRatePercent, Double averageDurationDays) {}
}
