package com.poultryprophet.task.dto;

import com.poultryprophet.task.TaskStatus;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;

public record UpdateTaskStatusRequest(@NotNull TaskStatus status, String note,
                                      UUID operationId, Instant occurredAt) {
    public UpdateTaskStatusRequest(TaskStatus status, String note) {
        this(status, note, null, null);
    }
}
