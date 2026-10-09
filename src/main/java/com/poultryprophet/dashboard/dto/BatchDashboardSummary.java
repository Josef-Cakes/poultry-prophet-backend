package com.poultryprophet.dashboard.dto;

import com.poultryprophet.alert.Severity;

import java.time.LocalDate;

/** Compact, factual values used by the farm dashboard batch cards. */
public record BatchDashboardSummary(
        long healthRelatedDeaths,
        long accidentalDeaths,
        long totalDeaths,
        long otherPopulationChanges,
        int recordedHealthEvents,
        int activeAlertCount,
        Severity highestActiveAlertSeverity,
        LocalDate lastEventDate
) {
}
