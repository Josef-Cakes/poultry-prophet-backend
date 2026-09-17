package com.poultryprophet.event;

import java.time.LocalDate;

/** Published after mortality accounting has atomically updated the canonical batch population. */
public record MortalityRecordedEvent(
        Long eventId,
        Long batchId,
        Long handlerId,
        LocalDate eventDate,
        int affectedCount,
        int remainingPopulation,
        String cause
) {
}
