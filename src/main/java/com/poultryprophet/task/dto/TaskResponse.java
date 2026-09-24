package com.poultryprophet.task.dto;

import com.poultryprophet.task.HandlerTask;
import java.time.Instant;

public record TaskResponse(
        Long id, Long farmId, Long batchId, Long incubationCycleId, String title, String instructions,
        Long assignedHandlerId, Long assignedManagerId, Instant dueAt, String priority, String status,
        boolean overdue, String completionNote, Instant completedAt, Instant createdAt, Instant updatedAt
) {
    public static TaskResponse from(HandlerTask task, Instant now) {
        boolean overdue = task.getDueAt() != null && task.getDueAt().isBefore(now)
                && (task.getStatus().name().equals("TODO") || task.getStatus().name().equals("IN_PROGRESS")
                    || task.getStatus().name().equals("BLOCKED"));
        return new TaskResponse(task.getId(), task.getFarmId(), task.getBatchId(), task.getIncubationCycleId(),
                task.getTitle(), task.getInstructions(), task.getAssignedHandlerId(), task.getAssignedManagerId(),
                task.getDueAt(), task.getPriority().name(), task.getStatus().name(), overdue,
                task.getCompletionNote(), task.getCompletedAt(), task.getCreatedAt(), task.getUpdatedAt());
    }
}
