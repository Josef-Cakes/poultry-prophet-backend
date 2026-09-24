package com.poultryprophet.analytics.dto;

import java.time.LocalDate;

public record TaskAnalyticsResponse(LocalDate startDate, LocalDate endDate, long totalTasks,
                                    long openTasks, long completedTasks, long overdueTasks,
                                    double completionRatePercent) {}
