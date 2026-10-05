package com.poultryprophet.analytics;

import com.poultryprophet.analytics.dto.BatchComparisonResponse;
import com.poultryprophet.security.CustomUserDetails;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/analytics/batches")
@PreAuthorize("hasRole('MANAGER')")
public class BatchComparisonController {
    private final BatchComparisonService service;

    public BatchComparisonController(BatchComparisonService service) {
        this.service = service;
    }

    @GetMapping("/compare")
    public BatchComparisonResponse compare(@RequestParam List<Long> batchIds,
                                           @RequestParam(defaultValue = "30") int windowDays,
                                           @RequestParam(defaultValue = "REAL") String origin,
                                           @AuthenticationPrincipal CustomUserDetails principal) {
        return service.compare(principal.getFarmId(), batchIds, windowDays, origin);
    }
}
