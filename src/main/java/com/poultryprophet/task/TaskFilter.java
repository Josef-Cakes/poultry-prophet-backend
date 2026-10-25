package com.poultryprophet.task;

/** Filters shared by the paged task list and its authoritative summary. */
public record TaskFilter(
        TaskView view,
        TaskDueBucket dueBucket,
        Long batchId,
        Boolean farmWide,
        Long assignedHandlerId,
        TaskPriority priority,
        String sourceType,
        String assignmentScope,
        String search
) {
    public static TaskFilter defaults() {
        return new TaskFilter(TaskView.OPEN, TaskDueBucket.ALL, null, null, null, null, null, null, null);
    }

    public TaskFilter normalized() {
        return new TaskFilter(
                view == null ? TaskView.OPEN : view,
                dueBucket == null ? TaskDueBucket.ALL : dueBucket,
                batchId,
                farmWide,
                assignedHandlerId,
                priority,
                normalize(sourceType),
                normalize(assignmentScope),
                normalize(search)
        );
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
