package com.poultryprophet.dashboard;

import com.poultryprophet.dashboard.dto.BatchDashboardResponse;
import com.poultryprophet.security.CustomUserDetails;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/batches")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/dashboard")
    public List<BatchDashboardResponse> dashboard(@AuthenticationPrincipal CustomUserDetails principal) {
        return dashboardService.listForFarm(principal.getFarmId());
    }
}
