package com.poultryprophet.task.dto;

import com.poultryprophet.task.TaskStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateTaskStatusRequest(@NotNull TaskStatus status, String note) {}
