package com.poultryprophet.sync;

import com.poultryprophet.batch.BatchService;
import com.poultryprophet.batch.dto.BatchResponse;
import com.poultryprophet.dashboard.DashboardService;
import com.poultryprophet.dashboard.dto.BatchDashboardResponse;
import com.poultryprophet.dashboard.OverviewService;
import com.poultryprophet.dashboard.dto.BatchOverviewResponse;
import com.poultryprophet.event.BatchEventService;
import com.poultryprophet.event.dto.BatchEventResponse;
import com.poultryprophet.input.FarmInputService;
import com.poultryprophet.input.dto.FarmInputLogResponse;
import com.poultryprophet.inventory.InventoryService;
import com.poultryprophet.inventory.dto.FarmProductResponse;
import com.poultryprophet.security.CustomUserDetails;
import com.poultryprophet.task.TaskService;
import com.poultryprophet.task.dto.TaskResponse;
import com.poultryprophet.vaccination.VaccinationService;
import com.poultryprophet.vaccination.dto.VaccinationPlanItemResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/offline")
public class OfflineBootstrapController {
    private final BatchService batchService;
    private final DashboardService dashboardService;
    private final InventoryService inventoryService;
    private final TaskService taskService;
    private final VaccinationService vaccinationService;
    private final BatchEventService eventService;
    private final FarmInputService inputService;
    private final OverviewService overviewService;

    public OfflineBootstrapController(BatchService batchService, DashboardService dashboardService,
                                      InventoryService inventoryService, TaskService taskService,
                                      VaccinationService vaccinationService, BatchEventService eventService,
                                      FarmInputService inputService, OverviewService overviewService) {
        this.batchService = batchService;
        this.dashboardService = dashboardService;
        this.inventoryService = inventoryService;
        this.taskService = taskService;
        this.vaccinationService = vaccinationService;
        this.eventService = eventService;
        this.inputService = inputService;
        this.overviewService = overviewService;
    }

    @GetMapping("/bootstrap")
    public OfflineBootstrapResponse bootstrap(@AuthenticationPrincipal CustomUserDetails principal) {
        Long farmId = principal.getFarmId();
        List<BatchResponse> batches = batchService.listForFarm(farmId, false);
        Map<Long, List<VaccinationPlanItemResponse>> plans = new LinkedHashMap<>();
        Map<Long, BatchOverviewResponse> overviews = new LinkedHashMap<>();
        Map<Long, List<BatchEventResponse>> events = new LinkedHashMap<>();
        Map<Long, List<FarmInputLogResponse>> inputs = new LinkedHashMap<>();
        for (BatchResponse batch : batches) {
            plans.put(batch.id(), vaccinationService.plan(batch.id(), farmId));
            overviews.put(batch.id(), overviewService.getOverview(batch.id(), farmId));
            events.put(batch.id(), eventService.recent(batch.id(), farmId, 100));
            // Field recording needs recent context, not the complete historical
            // log. The full history remains available from the paged endpoint.
            inputs.put(batch.id(), inputService.page(farmId, batch.id(), null, false, null, 0, 50).items());
        }
        boolean manager = principal.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_MANAGER"));
        List<TaskResponse> tasks = taskService.offlineTasks(farmId, principal.getId(), manager);
        List<FarmProductResponse> products = inventoryService.listProducts(farmId, false);
        List<BatchDashboardResponse> dashboard = dashboardService.listForFarm(farmId);
        return new OfflineBootstrapResponse(farmId, principal.getId(), principal.getUser().getRole().name(),
                Instant.now(), batches, dashboard, products, tasks, plans, overviews, events, inputs);
    }
}
