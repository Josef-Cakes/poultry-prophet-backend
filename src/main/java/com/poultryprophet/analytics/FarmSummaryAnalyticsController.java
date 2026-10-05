package com.poultryprophet.analytics;

import com.poultryprophet.analytics.dto.FarmSummaryAnalyticsResponse;
import com.poultryprophet.security.CustomUserDetails;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/analytics/operations/v2")
@PreAuthorize("hasRole('MANAGER')")
public class FarmSummaryAnalyticsController {
    private final FarmSummaryAnalyticsService service;

    public FarmSummaryAnalyticsController(FarmSummaryAnalyticsService service) {
        this.service = service;
    }

    @GetMapping
    public FarmSummaryAnalyticsResponse summary(
            @RequestParam(required = false, defaultValue = "FARM") String scope,
            @RequestParam(required = false) Long batchId,
            @RequestParam(required = false) LocalDate start,
            @RequestParam(required = false) LocalDate end,
            @RequestParam(required = false, defaultValue = "REAL") String origin,
            @RequestParam(required = false) String testRunId,
            @AuthenticationPrincipal CustomUserDetails principal) {
        return service.summarize(principal.getFarmId(), scope, batchId, start, end, origin, testRunId);
    }
}
