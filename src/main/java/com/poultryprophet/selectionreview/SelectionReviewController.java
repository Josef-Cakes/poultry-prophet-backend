package com.poultryprophet.selectionreview;

import com.poultryprophet.security.CustomUserDetails;
import jakarta.validation.Valid;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/batches/{batchId}")
public class SelectionReviewController {
    private final SelectionReviewService service;

    public SelectionReviewController(SelectionReviewService service) {
        this.service = service;
    }

    @GetMapping("/selection-review/preview")
    public SelectionReviewPayload preview(@PathVariable Long batchId,
                                          @RequestParam(required = false) LocalDate periodStart,
                                          @RequestParam(required = false) LocalDate periodEnd,
                                          @RequestParam(required = false) LocalDate asOfDate,
                                          @AuthenticationPrincipal CustomUserDetails principal) {
        return service.preview(batchId, principal.getFarmId(), periodStart, periodEnd, asOfDate, isManager(principal));
    }

    @PostMapping("/selection-reviews")
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseEntity<SelectionReviewResponse> create(@PathVariable Long batchId,
                                                           @RequestBody(required = false) CreateSelectionReviewRequest request,
                                                           @AuthenticationPrincipal CustomUserDetails principal) {
        return ResponseEntity.status(201).body(service.create(batchId, principal.getFarmId(), principal.getId(), request));
    }

    @GetMapping("/selection-reviews")
    public List<SelectionReviewResponse> list(@PathVariable Long batchId,
                                              @AuthenticationPrincipal CustomUserDetails principal) {
        return service.list(batchId, principal.getFarmId(), isManager(principal));
    }

    @GetMapping("/selection-reviews/{reviewId}")
    public SelectionReviewResponse get(@PathVariable Long batchId, @PathVariable Long reviewId,
                                       @AuthenticationPrincipal CustomUserDetails principal) {
        return service.get(batchId, principal.getFarmId(), reviewId, isManager(principal));
    }

    @PostMapping("/selection-reviews/{reviewId}/finalize")
    @PreAuthorize("hasRole('MANAGER')")
    public SelectionReviewResponse finalize(@PathVariable Long batchId, @PathVariable Long reviewId,
                                             @Valid @RequestBody FinalizeSelectionReviewRequest request,
                                             @AuthenticationPrincipal CustomUserDetails principal) {
        return service.finalize(batchId, principal.getFarmId(), reviewId, principal.getId(), request);
    }

    @GetMapping(value = "/selection-reviews/{reviewId}/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseEntity<byte[]> pdf(@PathVariable Long batchId, @PathVariable Long reviewId,
                                      @AuthenticationPrincipal CustomUserDetails principal) {
        byte[] bytes = service.exportPdf(batchId, principal.getFarmId(), reviewId);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename("selection-review-" + batchId + "-" + reviewId + ".pdf").build());
        return ResponseEntity.ok().headers(headers).body(bytes);
    }

    private boolean isManager(CustomUserDetails principal) {
        return principal.getAuthorities().stream().anyMatch(value -> "ROLE_MANAGER".equals(value.getAuthority()));
    }
}
