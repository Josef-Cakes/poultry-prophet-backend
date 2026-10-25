package com.poultryprophet.task.dto;

import com.poultryprophet.task.TaskPriority;
import com.poultryprophet.task.TaskAssignmentMode;
import jakarta.validation.constraints.NotBlank;

import java.time.Instant;

public record CreateTaskRequest(
        @NotBlank String title,
        String instructions,
        Long batchId,
        Long incubationCycleId,
        Long assignedHandlerId,
        Instant dueAt,
        TaskPriority priority,
        TaskAssignmentMode assignmentMode
) {
    /** Keeps existing internal callers compatible while the API adopts assignmentMode. */
    public CreateTaskRequest(String title, String instructions, Long batchId, Long incubationCycleId,
                             Long assignedHandlerId, Instant dueAt, TaskPriority priority) {
        this(title, instructions, batchId, incubationCycleId, assignedHandlerId, dueAt, priority,
                assignedHandlerId == null ? TaskAssignmentMode.ALL_HANDLERS : TaskAssignmentMode.SPECIFIC_HANDLER);
    }
}
