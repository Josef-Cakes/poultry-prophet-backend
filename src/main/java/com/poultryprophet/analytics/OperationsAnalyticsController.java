package com.poultryprophet.analytics;

import com.poultryprophet.analytics.dto.OperationsAnalyticsResponse;
import com.poultryprophet.security.CustomUserDetails;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/analytics/operations")
@PreAuthorize("hasRole('MANAGER')")
public class OperationsAnalyticsController {
    private final OperationsAnalyticsService service;
    public OperationsAnalyticsController(OperationsAnalyticsService service) { this.service = service; }

    @GetMapping
    public OperationsAnalyticsResponse summarize(@RequestParam(required = false) LocalDate start,
                                                  @RequestParam(required = false) LocalDate end,
                                                  @AuthenticationPrincipal CustomUserDetails principal) {
        return service.summarize(principal.getFarmId(), start, end);
    }
}
