package com.poultryprophet.task.dto;

import com.poultryprophet.task.TaskPriority;
import jakarta.validation.constraints.NotBlank;

import java.time.Instant;

public record CreateTaskRequest(
        @NotBlank String title,
        String instructions,
        Long batchId,
        Long incubationCycleId,
        Long assignedHandlerId,
        Instant dueAt,
        TaskPriority priority
) {}
