package com.poultryprophet.batch;

import com.poultryprophet.batch.dto.BatchResponse;
import com.poultryprophet.batch.dto.BatchTrackingResponse;
import com.poultryprophet.batch.dto.ChangeStageRequest;
import com.poultryprophet.batch.dto.CreateBatchRequest;
import com.poultryprophet.security.CustomUserDetails;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/batches")
public class BatchController {

    private final BatchService batchService;

    public BatchController(BatchService batchService) {
        this.batchService = batchService;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('MANAGER', 'HANDLER')")
    public ResponseEntity<BatchResponse> create(@Valid @RequestBody CreateBatchRequest request,
                                                @AuthenticationPrincipal CustomUserDetails principal) {
        BatchResponse response = batchService.create(request, principal.getFarmId());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public List<BatchResponse> list(@RequestParam(defaultValue = "false") boolean archived,
                                    @AuthenticationPrincipal CustomUserDetails principal) {
        return batchService.listForFarm(principal.getFarmId(), archived);
    }

    @GetMapping("/{id}")
    public BatchResponse get(@PathVariable Long id, @AuthenticationPrincipal CustomUserDetails principal) {
        return batchService.getForFarm(id, principal.getFarmId());
    }

    @GetMapping("/{id}/tracking")
    public BatchTrackingResponse getTracking(@PathVariable Long id,
                                             @AuthenticationPrincipal CustomUserDetails principal) {
        return batchService.getTracking(id, principal.getFarmId());
    }

    @PatchMapping("/{id}/stage")
    @PreAuthorize("hasRole('MANAGER')")
    public BatchResponse changeStage(@PathVariable Long id,
                                     @Valid @RequestBody ChangeStageRequest request,
                                     @AuthenticationPrincipal CustomUserDetails principal) {
        return batchService.changeStage(id, principal.getFarmId(), request.stageId());
    }

    /** Clears a manual stage override so the batch's stage tracks its age again. */
    @PatchMapping("/{id}/stage/auto")
    @PreAuthorize("hasRole('MANAGER')")
    public BatchResponse useAutoStage(@PathVariable Long id,
                                      @AuthenticationPrincipal CustomUserDetails principal) {
        return batchService.useAutoStage(id, principal.getFarmId());
    }

    @PatchMapping("/{id}/archive")
    @PreAuthorize("hasRole('MANAGER')")
    public BatchResponse archive(@PathVariable Long id,
                                 @AuthenticationPrincipal CustomUserDetails principal) {
        return batchService.archive(id, principal.getFarmId());
    }

    @PatchMapping("/{id}/restore")
    @PreAuthorize("hasRole('MANAGER')")
    public BatchResponse restore(@PathVariable Long id,
                                 @AuthenticationPrincipal CustomUserDetails principal) {
        return batchService.restore(id, principal.getFarmId());
    }
}
