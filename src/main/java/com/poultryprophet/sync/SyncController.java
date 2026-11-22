package com.poultryprophet.sync;

import com.poultryprophet.security.CustomUserDetails;
import com.poultryprophet.sync.dto.SyncBatchRequest;
import com.poultryprophet.sync.dto.SyncBatchResponse;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/sync")
public class SyncController {

    private final SyncService syncService;

    public SyncController(SyncService syncService) {
        this.syncService = syncService;
    }

    /** Legacy compatibility only; changed records are never clock-authorized overwrites. */
    @Deprecated
    @PostMapping("/batch")
    public SyncBatchResponse syncBatch(@Valid @RequestBody SyncBatchRequest request,
                                       @AuthenticationPrincipal CustomUserDetails principal) {
        return syncService.sync(request, principal.getFarmId(), principal.getId());
    }
}
