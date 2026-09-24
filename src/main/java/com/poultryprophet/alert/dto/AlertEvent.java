package com.poultryprophet.alert.dto;

import com.poultryprophet.alert.Alert;
import com.poultryprophet.alert.Severity;

import java.time.Instant;
import java.time.LocalDate;

/** SDD 3.3: self-contained payload broadcast over the socket when an alert is created. */
public record AlertEvent(
        Long alertId,
        Long farmId,
        Long batchId,
        Long sourceEventId,
        Severity severity,
        String indicatorType,
        String summary,
        String batchName,
        String handlerName,
        Integer deathCount,
        String cause,
        LocalDate occurrenceDate,
        Instant occurredAt
) {
    public static AlertEvent from(Alert a) {
        return new AlertEvent(
                a.getId(),
                a.getBatch().getFarmId(),
                a.getBatch().getId(),
                a.getSourceEvent() != null ? a.getSourceEvent().getId() : null,
                a.getSeverity(),
                a.getIndicatorType(),
                a.getMessage(),
                a.getBatchName(),
                a.getHandlerName(),
                a.getDeathCount(),
                a.getCause(),
                a.getOccurrenceDate(),
                a.getCreatedAt());
    }
}
