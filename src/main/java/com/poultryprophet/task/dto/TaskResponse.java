package com.poultryprophet.task.dto;

import com.poultryprophet.task.HandlerTask;
import java.time.Instant;
import java.time.ZoneId;

public record TaskResponse(
        Long id, Long farmId, Long batchId, Long incubationCycleId, String title, String instructions,
        Long assignedHandlerId, Long assignedManagerId, Instant dueAt, String priority, String status,
        boolean overdue, String completionNote, Instant completedAt, Instant createdAt, Instant updatedAt,
        String assignmentScope, Instant visibleFrom, String sourceType, Long sourceId, Long completedBy
) {
    public static TaskResponse from(HandlerTask task, Instant now) {
        return from(task, now, ZoneId.of("Asia/Manila"));
    }

    public static TaskResponse from(HandlerTask task, Instant now, ZoneId zone) {
        boolean allDay = "VACCINATION_PLAN".equals(task.getSourceType());
        boolean overdueByDate = task.getDueAt() != null
                && task.getDueAt().atZone(zone).toLocalDate().isBefore(now.atZone(zone).toLocalDate());
        boolean overdueByTime = task.getDueAt() != null && task.getDueAt().isBefore(now);
        boolean overdue = task.getDueAt() != null && (allDay ? overdueByDate : overdueByTime)
                && (task.getStatus().name().equals("TODO") || task.getStatus().name().equals("IN_PROGRESS")
                    || task.getStatus().name().equals("BLOCKED"));
        return new TaskResponse(task.getId(), task.getFarmId(), task.getBatchId(), task.getIncubationCycleId(),
                task.getTitle(), task.getInstructions(), task.getAssignedHandlerId(), task.getAssignedManagerId(),
                task.getDueAt(), task.getPriority().name(), task.getStatus().name(), overdue,
                task.getCompletionNote(), task.getCompletedAt(), task.getCreatedAt(), task.getUpdatedAt(),
                task.getAssignmentScope(), task.getVisibleFrom(), task.getSourceType(), task.getSourceId(), task.getCompletedBy());
    }
}
