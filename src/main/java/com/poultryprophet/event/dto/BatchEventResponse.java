package com.poultryprophet.event.dto;

import com.poultryprophet.event.BatchEvent;
import com.poultryprophet.event.EventType;
import com.poultryprophet.event.SalePurpose;

import java.time.Instant;
import java.time.LocalDate;
import java.math.BigDecimal;
import java.util.UUID;

public record BatchEventResponse(
        Long id,
        Long batchId,
        Long handlerId,
        String handlerName,
        LocalDate eventDate,
        EventType eventType,
        String severityLabel,
        String concernLevel,
        int affectedCount,
        String title,
        String details,
        String tags,
        Instant createdAt,
        UUID operationId,
        Integer populationDelta,
        Integer populationAfter,
        Integer remainingPopulation,
        SalePurpose salePurpose,
        Integer maleDelta,
        Integer femaleDelta,
        Integer unclassifiedDelta,
        Instant occurredAt,
        BigDecimal saleAmount,
        String saleCurrency,
        String saleBuyer,
        String saleReference
) {
    public static BatchEventResponse from(BatchEvent e, String handlerName) {
        return from(e, handlerName, null);
    }

    public static BatchEventResponse from(BatchEvent e, String handlerName, Integer remainingPopulation) {
        return new BatchEventResponse(
                e.getId(), e.getBatchId(), e.getHandlerId(), handlerName,
                e.getEventDate(), e.getEventType(), e.getSeverityLabel(),
                normalizeConcern(e.getSeverityLabel()),
                e.getAffectedCount(), e.getTitle(), e.getDetails(),
                e.getTags(), e.getCreatedAt(), e.getOperationId(), e.getPopulationDelta(),
                e.getPopulationAfter(), remainingPopulation, e.getSalePurpose(),
                e.getMaleDelta(), e.getFemaleDelta(), e.getUnclassifiedDelta(), e.getOccurredAt(),
                e.getSaleAmount(), e.getSaleCurrency(), e.getSaleBuyer(), e.getSaleReference());
    }

    private static String normalizeConcern(String value) {
        if (value == null || value.isBlank()) return "UNRATED";
        return switch (value.trim().toUpperCase()) {
            case "MINOR", "LOW" -> "LOW";
            case "MODERATE", "MEDIUM" -> "MEDIUM";
            case "MAJOR", "HIGH" -> "HIGH";
            default -> "UNRATED";
        };
    }
}
