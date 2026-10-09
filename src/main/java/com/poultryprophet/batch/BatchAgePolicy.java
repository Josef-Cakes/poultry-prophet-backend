package com.poultryprophet.batch;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/** Single age policy used by lifecycle, vaccination and report calculations. Hatch day is day 0. */
public final class BatchAgePolicy {
    private BatchAgePolicy() {}

    public static long ageDays(LocalDate hatchDate, LocalDate asOfDate) {
        if (hatchDate == null || asOfDate == null) return 0;
        return Math.max(0, ChronoUnit.DAYS.between(hatchDate, asOfDate));
    }

    public static String stageName(long ageDays) {
        if (ageDays <= 30) return "brooding";
        if (ageDays <= 120) return "ranging";
        return "pre-conditioning";
    }
}
