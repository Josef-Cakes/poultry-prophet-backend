package com.poultryprophet.event.dto;

import java.util.List;
import java.util.Map;

public record BatchEventReportResponse(
        long totalRecords,
        long deathRecords,
        long birdsLost,
        long healthConcerns,
        long populationMovements,
        long vaccineMedicineRecords,
        List<CountPoint> overTime,
        Map<String, Long> categoryDistribution,
        Map<String, Long> concernDistribution
) {
    public record CountPoint(String date, long count) {}
}
