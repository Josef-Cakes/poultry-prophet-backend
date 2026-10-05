package com.poultryprophet.selectionsession;

import com.poultryprophet.security.CustomUserDetails;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/batches/{batchId}/selection-sessions")
@PreAuthorize("hasRole('MANAGER')")
public class SelectionSessionController {
    private final SelectionSessionService service;

    public SelectionSessionController(SelectionSessionService service) {
        this.service = service;
    }

    @PostMapping
    public SelectionSessionResponse create(@PathVariable Long batchId,
                                           @Valid @RequestBody CreateSelectionSessionRequest request,
                                           @AuthenticationPrincipal CustomUserDetails principal) {
        return service.create(batchId, principal.getFarmId(), principal.getId(), request, null);
    }

    @GetMapping
    public List<SelectionSessionResponse> list(@PathVariable Long batchId,
                                               @AuthenticationPrincipal CustomUserDetails principal) {
        return service.list(batchId, principal.getFarmId());
    }

    @GetMapping("/{sessionId}")
    public SelectionSessionResponse get(@PathVariable Long batchId, @PathVariable Long sessionId,
                                        @AuthenticationPrincipal CustomUserDetails principal) {
        return service.get(batchId, principal.getFarmId(), sessionId);
    }

    @PatchMapping("/{sessionId}")
    public SelectionSessionResponse update(@PathVariable Long batchId, @PathVariable Long sessionId,
                                           @Valid @RequestBody CreateSelectionSessionRequest request,
                                           @AuthenticationPrincipal CustomUserDetails principal) {
        return service.updateDraft(batchId, principal.getFarmId(), sessionId, request);
    }

    @PostMapping("/{sessionId}/finalize")
    public SelectionSessionResponse finalize(@PathVariable Long batchId, @PathVariable Long sessionId,
                                             @AuthenticationPrincipal CustomUserDetails principal) {
        return service.finalize(batchId, principal.getFarmId(), sessionId);
    }

    @PostMapping("/{sessionId}/supersede")
    public SelectionSessionResponse supersede(@PathVariable Long batchId, @PathVariable Long sessionId,
                                              @Valid @RequestBody CreateSelectionSessionRequest request,
                                              @AuthenticationPrincipal CustomUserDetails principal) {
        return service.supersede(batchId, principal.getFarmId(), sessionId, principal.getId(), request);
    }
}
