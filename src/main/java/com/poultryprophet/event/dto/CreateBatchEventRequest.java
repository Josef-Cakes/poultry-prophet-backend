package com.poultryprophet.event.dto;

import com.poultryprophet.event.EventType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.UUID;

public record CreateBatchEventRequest(
        LocalDate eventDate,
        @NotNull EventType eventType,
        @NotBlank String title,
        String severityLabel,
        @Min(0) int affectedCount,
        String details,
        String tags,
        @NotNull UUID operationId,
        Integer populationDelta
) {
    /** Compatibility constructor for existing unit fixtures; real clients must send operationId. */
    public CreateBatchEventRequest(LocalDate eventDate,
                                   EventType eventType,
                                   String title,
                                   String severityLabel,
                                   int affectedCount,
                                   String details,
                                   String tags) {
        this(eventDate, eventType, title, severityLabel, affectedCount, details, tags,
                UUID.randomUUID(), null);
    }
}
