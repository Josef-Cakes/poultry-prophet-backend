package com.poultryprophet.alert;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.poultryprophet.alert.dto.AcknowledgeRequest;
import com.poultryprophet.alert.dto.AlertResponse;
import com.poultryprophet.common.PageResponse;
import com.poultryprophet.security.CustomUserDetails;

@RestController
@RequestMapping("/api")
public class AlertController {

    private final AlertService alertService;

    public AlertController(AlertService alertService) {
        this.alertService = alertService;
    }

    @GetMapping("/alerts")
    public List<AlertResponse> listForFarm(@RequestParam(defaultValue = "true") boolean activeOnly,
                                           @RequestParam(defaultValue = "100") int limit,
                                           @AuthenticationPrincipal CustomUserDetails principal) {
        return alertService.listForFarm(principal.getFarmId(), activeOnly, limit);
    }

    @GetMapping("/alerts/page")
    public PageResponse<AlertResponse> pageForFarm(@RequestParam(defaultValue = "true") boolean activeOnly,
                                                   @RequestParam(required = false) Severity severity,
                                                   @RequestParam(required = false) Long batchId,
                                                   @RequestParam(defaultValue = "priority") String sort,
                                                   @RequestParam(defaultValue = "0") int page,
                                                   @RequestParam(defaultValue = "20") int size,
                                                   @AuthenticationPrincipal CustomUserDetails principal) {
        return alertService.pageForFarm(principal.getFarmId(), activeOnly, severity, batchId, sort, page, size);
    }

    @GetMapping("/alerts/summary")
    public AlertSummaryResponse summary(@AuthenticationPrincipal CustomUserDetails principal) {
        return alertService.summaryForFarm(principal.getFarmId());
    }

    @GetMapping("/batches/{batchId}/alerts")
    public List<AlertResponse> list(@PathVariable Long batchId,
                                    @RequestParam(defaultValue = "false") boolean activeOnly,
                                    @RequestParam(defaultValue = "50") int limit,
                                    @AuthenticationPrincipal CustomUserDetails principal) {
        return alertService.list(batchId, principal.getFarmId(), activeOnly, limit);
    }

    @PostMapping("/alerts/{id}/acknowledge")
    @PreAuthorize("hasRole('MANAGER')")
    public AlertResponse acknowledge(@PathVariable Long id,
                                     @RequestBody(required = false) AcknowledgeRequest request,
                                     @AuthenticationPrincipal CustomUserDetails principal) {
        String note = request != null ? request.note() : null;
        return alertService.acknowledge(id, principal.getFarmId(), principal.getId(), note);
    }
}
