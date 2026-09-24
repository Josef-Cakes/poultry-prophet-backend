package com.poultryprophet.incubation.dto;

import com.poultryprophet.incubation.IncubationCycle;
import com.poultryprophet.incubation.IncubationStatus;

import java.time.Instant;
import java.time.LocalDate;

public record IncubationCycleResponse(
        Long id,
        Long farmId,
        String cycleName,
        String incubatorCode,
        String eggSource,
        String bloodline,
        LocalDate loadedDate,
        int eggsLoaded,
        LocalDate expectedHatchDate,
        LocalDate actualHatchDate,
        Integer hatchedCount,
        Integer unhatchedCount,
        Integer removedDamagedCount,
        IncubationStatus status,
        String notes,
        Long closedBy,
        Instant closedAt,
        Long createdBatchId,
        Instant createdAt
) {
    public static IncubationCycleResponse from(IncubationCycle c) {
        return new IncubationCycleResponse(c.getId(), c.getFarmId(), c.getCycleName(), c.getIncubatorCode(),
                c.getEggSource(), c.getBloodline(), c.getLoadedDate(), c.getEggsLoaded(),
                c.getExpectedHatchDate(), c.getActualHatchDate(), c.getHatchedCount(),
                c.getUnhatchedCount(), c.getRemovedDamagedCount(), c.getStatus(), c.getNotes(),
                c.getClosedBy(), c.getClosedAt(), c.getCreatedBatchId(), c.getCreatedAt());
    }
}
