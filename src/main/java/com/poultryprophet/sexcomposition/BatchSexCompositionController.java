package com.poultryprophet.sexcomposition;

import com.poultryprophet.security.CustomUserDetails;
import com.poultryprophet.sexcomposition.dto.CreateSexCompositionRequest;
import com.poultryprophet.sexcomposition.dto.SexCompositionResponse;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/batches/{batchId}/sex-composition")
public class BatchSexCompositionController {
    private final BatchSexCompositionService service;
    public BatchSexCompositionController(BatchSexCompositionService service) { this.service = service; }
    @GetMapping public SexCompositionResponse current(@PathVariable Long batchId, @AuthenticationPrincipal CustomUserDetails principal) { return service.current(batchId, principal.getFarmId()); }
    @GetMapping("/history") public List<SexCompositionResponse> history(@PathVariable Long batchId, @AuthenticationPrincipal CustomUserDetails principal) { return service.history(batchId, principal.getFarmId()); }
    @PostMapping @org.springframework.security.access.prepost.PreAuthorize("hasAnyRole('MANAGER','HANDLER')")
    public SexCompositionResponse record(@PathVariable Long batchId, @Valid @RequestBody CreateSexCompositionRequest request, @AuthenticationPrincipal CustomUserDetails principal) {
        return service.record(batchId, principal.getFarmId(), principal.getId(), request);
    }
}
