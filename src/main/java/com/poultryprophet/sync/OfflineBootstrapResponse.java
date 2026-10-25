package com.poultryprophet.sync;

import com.poultryprophet.batch.dto.BatchResponse;
import com.poultryprophet.dashboard.dto.BatchDashboardResponse;
import com.poultryprophet.dashboard.dto.BatchOverviewResponse;
import com.poultryprophet.event.dto.BatchEventResponse;
import com.poultryprophet.input.dto.FarmInputLogResponse;
import com.poultryprophet.inventory.dto.FarmProductResponse;
import com.poultryprophet.task.dto.TaskResponse;
import com.poultryprophet.vaccination.dto.VaccinationPlanItemResponse;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Minimal farm-scoped working set required for field recording while disconnected. */
public record OfflineBootstrapResponse(
        Long farmId,
        Long userId,
        String role,
        Instant generatedAt,
        List<BatchResponse> batches,
        List<BatchDashboardResponse> dashboard,
        List<FarmProductResponse> products,
        List<TaskResponse> tasks,
        Map<Long, List<VaccinationPlanItemResponse>> vaccinationPlans,
        Map<Long, BatchOverviewResponse> overviews,
        Map<Long, List<BatchEventResponse>> events,
        Map<Long, List<FarmInputLogResponse>> inputs
) {}
