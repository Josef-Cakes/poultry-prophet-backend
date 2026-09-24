package com.poultryprophet.alert.dto;

import com.poultryprophet.alert.Alert;
import com.poultryprophet.alert.Severity;

import java.time.Instant;
import java.time.LocalDate;

public record AlertResponse(
        Long id,
        Long batchId,
        Long sourceEventId,
        Long indicatorId,
        String indicatorType,
        String batchName,
        String handlerName,
        Integer deathCount,
        String cause,
        LocalDate occurrenceDate,
        Severity severity,
        String message,
        boolean acknowledged,
        Long acknowledgedByUserId,
        Instant acknowledgedAt,
        String acknowledgmentNote,
        Instant createdAt
) {
    /** Must be invoked inside an open persistence context (lazy associations). */
    public static AlertResponse from(Alert a) {
        return new AlertResponse(
                a.getId(),
                a.getBatch().getId(),
                a.getSourceEvent() != null ? a.getSourceEvent().getId() : null,
                a.getIndicator() != null ? a.getIndicator().getId() : null,
                a.getIndicatorType(),
                a.getBatchName(),
                a.getHandlerName(),
                a.getDeathCount(),
                a.getCause(),
                a.getOccurrenceDate(),
                a.getSeverity(),
                a.getMessage(),
                a.getAcknowledgedAt() != null,
                a.getAcknowledgedBy() != null ? a.getAcknowledgedBy().getId() : null,
                a.getAcknowledgedAt(),
                a.getAcknowledgmentNote(),
                a.getCreatedAt());
    }
}
