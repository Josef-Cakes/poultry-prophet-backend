package com.poultryprophet.alert;

import com.poultryprophet.alert.dto.AcknowledgeRequest;
import com.poultryprophet.alert.dto.AlertResponse;
import com.poultryprophet.security.CustomUserDetails;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

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

    @GetMapping("/batches/{batchId}/alerts")
    public List<AlertResponse> list(@PathVariable Long batchId,
                                    @RequestParam(defaultValue = "false") boolean activeOnly,
                                    @RequestParam(defaultValue = "50") int limit,
                                    @AuthenticationPrincipal CustomUserDetails principal) {
        return alertService.list(batchId, principal.getFarmId(), activeOnly, limit);
    }

    /** Farm-wide notifications centre: alerts across every batch on the caller's farm. */
    @GetMapping("/alerts")
    public List<AlertResponse> listFarm(@RequestParam(defaultValue = "true") boolean activeOnly,
                                        @RequestParam(defaultValue = "100") int limit,
                                        @AuthenticationPrincipal CustomUserDetails principal) {
        return alertService.listForFarm(principal.getFarmId(), activeOnly, limit);
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
