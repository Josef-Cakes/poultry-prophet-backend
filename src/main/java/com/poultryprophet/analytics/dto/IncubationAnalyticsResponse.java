package com.poultryprophet.analytics.dto;

import java.time.LocalDate;

public record IncubationAnalyticsResponse(
        LocalDate startDate, LocalDate endDate, long totalCycles, long completedCycles,
        int eggsLoaded, int hatched, int unhatched, int removedOrDamaged,
        double hatchRatePercent, double averageDurationDays) {}
