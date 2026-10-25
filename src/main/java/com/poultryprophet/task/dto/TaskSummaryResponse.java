package com.poultryprophet.task.dto;

public record TaskSummaryResponse(
        long open,
        long late,
        long today,
        long upcoming,
        long noDueDate,
        long completed,
        long cancelled
) {}
