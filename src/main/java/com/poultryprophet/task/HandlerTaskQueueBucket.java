package com.poultryprophet.task;

/** Handler-facing queues are intentionally separate from the manager's due-date filters. */
public enum HandlerTaskQueueBucket {
    NOW,
    UPCOMING,
    COMPLETED
}
