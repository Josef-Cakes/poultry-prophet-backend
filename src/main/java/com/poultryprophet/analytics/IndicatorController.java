package com.poultryprophet.analytics;

import com.poultryprophet.analytics.dto.IndicatorResponse;
import com.poultryprophet.security.CustomUserDetails;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
/** Legacy score history; active UI uses Selection Review Report data instead. */
@RequestMapping("/api/legacy/batches/{batchId}/indicators")
public class IndicatorController {

    private final IndicatorQueryService indicatorQueryService;

    public IndicatorController(IndicatorQueryService indicatorQueryService) {
        this.indicatorQueryService = indicatorQueryService;
    }

    @GetMapping("/latest")
    public IndicatorResponse latest(@PathVariable Long batchId,
                                    @AuthenticationPrincipal CustomUserDetails principal) {
        return indicatorQueryService.latest(batchId, principal.getFarmId());
    }

    @GetMapping
    public List<IndicatorResponse> recent(@PathVariable Long batchId,
                                          @RequestParam(defaultValue = "14") int limit,
                                          @AuthenticationPrincipal CustomUserDetails principal) {
        return indicatorQueryService.recent(batchId, principal.getFarmId(), limit);
    }
}
