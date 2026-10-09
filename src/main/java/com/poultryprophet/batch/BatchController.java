package com.poultryprophet.batch;

import com.poultryprophet.batch.dto.BatchResponse;
import com.poultryprophet.batch.dto.BatchTrackingResponse;
import com.poultryprophet.batch.dto.ArchiveBatchRequest;
import com.poultryprophet.batch.dto.BatchRetirementImpactResponse;
import com.poultryprophet.batch.dto.CreateBatchRequest;
import com.poultryprophet.batch.dto.ConfirmHatchDateRequest;
import com.poultryprophet.batch.dto.DeleteBatchRequest;
import com.poultryprophet.security.CustomUserDetails;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
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
    private final BatchRetirementService retirementService;
    private final BatchPopulationReconciliationService populationReconciliationService;

    public BatchController(BatchService batchService,
                           BatchRetirementService retirementService,
                           BatchPopulationReconciliationService populationReconciliationService) {
        this.batchService = batchService;
        this.retirementService = retirementService;
        this.populationReconciliationService = populationReconciliationService;
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

    @PatchMapping("/{id}/hatch-date")
    @PreAuthorize("hasRole('MANAGER')")
    public BatchResponse confirmHatchDate(@PathVariable Long id, @Valid @RequestBody ConfirmHatchDateRequest request,
                                          @AuthenticationPrincipal CustomUserDetails principal) {
        return batchService.confirmHatchDate(id, principal.getFarmId(), principal.getId(), request);
    }

    @PatchMapping("/{id}/population/reconcile")
    @PreAuthorize("hasRole('MANAGER')")
    public BatchResponse reconcilePopulation(@PathVariable Long id,
                                             @AuthenticationPrincipal CustomUserDetails principal) {
        return populationReconciliationService.reconcile(id, principal.getFarmId(), principal.getId());
    }

    @GetMapping("/{id}/retirement-impact")
    @PreAuthorize("hasRole('MANAGER')")
    public BatchRetirementImpactResponse retirementImpact(@PathVariable Long id,
                                                          @AuthenticationPrincipal CustomUserDetails principal) {
        return retirementService.impact(id, principal.getFarmId());
    }

    @PatchMapping("/{id}/archive")
    @PreAuthorize("hasRole('MANAGER')")
    public BatchResponse archive(@PathVariable Long id,
                                 @RequestBody(required = false) ArchiveBatchRequest request,
                                 @AuthenticationPrincipal CustomUserDetails principal) {
        return retirementService.archive(id, principal.getFarmId(), principal.getId(), request);
    }

    @PatchMapping("/{id}/restore")
    @PreAuthorize("hasRole('MANAGER')")
    public BatchResponse restore(@PathVariable Long id,
                                 @AuthenticationPrincipal CustomUserDetails principal) {
        return retirementService.restore(id, principal.getFarmId(), principal.getId());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseEntity<Void> delete(@PathVariable Long id,
                                       @RequestBody DeleteBatchRequest request,
                                       @AuthenticationPrincipal CustomUserDetails principal) {
        retirementService.delete(id, principal.getFarmId(), principal.getId(), request);
        return ResponseEntity.noContent().build();
    }
}
