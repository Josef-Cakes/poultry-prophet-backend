package com.poultryprophet.event.dto;

import com.poultryprophet.event.BatchEvent;
import com.poultryprophet.event.EventType;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record BatchEventResponse(
        Long id,
        Long batchId,
        Long handlerId,
        String handlerName,
        LocalDate eventDate,
        EventType eventType,
        String severityLabel,
        int affectedCount,
        String title,
        String details,
        String tags,
        Instant createdAt,
        UUID operationId,
        Integer populationDelta,
        Integer populationAfter,
        Integer remainingPopulation
) {
    public static BatchEventResponse from(BatchEvent e, String handlerName) {
        return from(e, handlerName, null);
    }

    public static BatchEventResponse from(BatchEvent e, String handlerName, Integer remainingPopulation) {
        return new BatchEventResponse(
                e.getId(), e.getBatchId(), e.getHandlerId(), handlerName,
                e.getEventDate(), e.getEventType(), e.getSeverityLabel(),
                e.getAffectedCount(), e.getTitle(), e.getDetails(),
                e.getTags(), e.getCreatedAt(), e.getOperationId(), e.getPopulationDelta(),
                e.getPopulationAfter(), remainingPopulation);
    }
}
