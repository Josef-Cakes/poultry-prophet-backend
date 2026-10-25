package com.poultryprophet.task.dto;

public record HandlerTaskQueueSummary(
        long now,
        long upcoming,
        long completed,
        long vaccinationReminders
) {}
