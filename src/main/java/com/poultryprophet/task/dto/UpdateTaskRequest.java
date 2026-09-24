package com.poultryprophet.task.dto;

import com.poultryprophet.task.TaskPriority;
import com.poultryprophet.task.TaskStatus;

import java.time.Instant;

public record UpdateTaskRequest(
        String title,
        String instructions,
        Long batchId,
        Long incubationCycleId,
        Long assignedHandlerId,
        Instant dueAt,
        TaskPriority priority,
        TaskStatus status,
        String completionNote
) {}
