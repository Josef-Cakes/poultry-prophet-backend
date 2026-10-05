package com.poultryprophet.dashboard.dto;

import com.poultryprophet.batch.dto.BatchResponse;

public record BatchDashboardResponse(
        BatchResponse batch,
        BatchDashboardSummary summary
) {
}
