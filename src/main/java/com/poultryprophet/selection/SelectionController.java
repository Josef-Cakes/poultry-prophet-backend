package com.poultryprophet.selection;

import com.poultryprophet.security.CustomUserDetails;
import com.poultryprophet.selection.dto.SelectionDecisionRequest;
import com.poultryprophet.selection.dto.SelectionRowResponse;
import com.poultryprophet.selection.dto.SelectionViewResponse;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Legacy endpoint retained under an explicit legacy namespace for historical/demo data only.
 * The active workflow is {@link com.poultryprophet.selectionreview.SelectionReviewController}.
 */
@RestController
@RequestMapping("/api/legacy/batches/{batchId}/selection")
public class SelectionController {

    private final SelectionService selectionService;

    public SelectionController(SelectionService selectionService) {
        this.selectionService = selectionService;
    }

    @GetMapping
    @PreAuthorize("hasRole('MANAGER')")
    public SelectionViewResponse view(@PathVariable Long batchId,
                                      @AuthenticationPrincipal CustomUserDetails principal) {
        return selectionService.getSelectionView(batchId, principal.getFarmId());
    }

    @PostMapping("/birds/{birdId}")
    @PreAuthorize("hasRole('MANAGER')")
    public SelectionRowResponse decide(@PathVariable Long batchId,
                                       @PathVariable Long birdId,
                                       @Valid @RequestBody SelectionDecisionRequest request,
                                       @AuthenticationPrincipal CustomUserDetails principal) {
        return selectionService.decide(batchId, birdId, principal.getFarmId(), principal.getId(), request);
    }
}
