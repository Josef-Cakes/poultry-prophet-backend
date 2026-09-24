package com.poultryprophet.record.dto;

import com.poultryprophet.record.DailyRecord;
import com.poultryprophet.record.ObservationQuality;
import com.poultryprophet.record.SyncStatus;

import java.time.Instant;
import java.time.LocalDate;

public record DailyRecordResponse(
        Long id,
        Long batchId,
        Long handlerId,
        String handlerName,
        LocalDate recordDate,
        double temperatureC,
        int mortalityCount,
        Double feedIntakeG,
        Double waterIntakeMl,
        ObservationQuality temperatureQuality,
        ObservationQuality feedQuality,
        ObservationQuality waterQuality,
        String behaviorNotes,
        SyncStatus syncStatus,
        Instant createdAt
) {
    public static DailyRecordResponse from(DailyRecord r) {
        return new DailyRecordResponse(
                r.getId(),
                r.getBatch().getId(),
                r.getHandler().getId(),
                r.getHandler().getFullName(),
                r.getRecordDate(),
                r.getTemperatureC(),
                r.getMortalityCount(),
                r.getFeedIntakeG(),
                r.getWaterIntakeMl(),
                r.getTemperatureQuality(),
                r.getFeedQuality(),
                r.getWaterQuality(),
                r.getBehaviorNotes(),
                r.getSyncStatus(),
                r.getCreatedAt());
    }
}
