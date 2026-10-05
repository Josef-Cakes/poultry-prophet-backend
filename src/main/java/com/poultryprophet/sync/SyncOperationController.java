package com.poultryprophet.sync;

import com.poultryprophet.security.CustomUserDetails;
import com.poultryprophet.sync.dto.SyncOperationsRequest;
import com.poultryprophet.sync.dto.SyncOperationsResponse;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/sync/v2")
public class SyncOperationController {
    private final SyncOperationService service;

    public SyncOperationController(SyncOperationService service) { this.service = service; }

    @PostMapping("/operations")
    public SyncOperationsResponse sync(@Valid @RequestBody SyncOperationsRequest request,
                                       @AuthenticationPrincipal CustomUserDetails principal) {
        return service.sync(request, principal.getFarmId(), principal.getId(), principal.getUser().getRole());
    }
}
