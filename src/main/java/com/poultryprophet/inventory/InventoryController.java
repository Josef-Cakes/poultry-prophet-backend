package com.poultryprophet.inventory;

import com.poultryprophet.inventory.dto.*;
import com.poultryprophet.input.dto.FarmInputLogResponse;
import com.poultryprophet.security.CustomUserDetails;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/inventory")
public class InventoryController {
    private final InventoryService service;

    public InventoryController(InventoryService service) { this.service = service; }

    @GetMapping("/products")
    public List<FarmProductResponse> products(@RequestParam(defaultValue = "false") boolean includeInactive,
                                              @AuthenticationPrincipal CustomUserDetails principal) {
        return service.listProducts(principal.getFarmId(), includeInactive);
    }

    @PostMapping("/products")
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseEntity<FarmProductResponse> create(@Valid @RequestBody CreateFarmProductRequest request,
                                                      @AuthenticationPrincipal CustomUserDetails principal) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createProduct(principal.getFarmId(), principal.getId(), request));
    }

    @PostMapping("/products/{productId}/stock-ins")
    @PreAuthorize("hasRole('MANAGER')")
    public InventoryMovementResponse stockIn(@PathVariable Long productId, @Valid @RequestBody StockInRequest request,
                                             @AuthenticationPrincipal CustomUserDetails principal) {
        return service.stockIn(principal.getFarmId(), principal.getId(), productId, request);
    }

    @PostMapping("/products/{productId}/adjustments")
    @PreAuthorize("hasRole('MANAGER')")
    public InventoryMovementResponse adjust(@PathVariable Long productId, @Valid @RequestBody InventoryAdjustmentRequest request,
                                            @AuthenticationPrincipal CustomUserDetails principal) {
        return service.adjust(principal.getFarmId(), principal.getId(), productId, request);
    }

    @GetMapping("/movements")
    @PreAuthorize("hasRole('MANAGER')")
    public List<InventoryMovementResponse> movements(@RequestParam(required = false) Long productId,
                                                     @RequestParam(required = false) Long batchId,
                                                     @AuthenticationPrincipal CustomUserDetails principal) {
        return service.movements(principal.getFarmId(), productId, batchId);
    }

    @GetMapping("/pending-review")
    @PreAuthorize("hasRole('MANAGER')")
    public List<FarmInputLogResponse> pendingReview(@AuthenticationPrincipal CustomUserDetails principal) {
        return service.pendingUses(principal.getFarmId());
    }

    @PostMapping("/pending-review/{inputId}/apply")
    @PreAuthorize("hasRole('MANAGER')")
    public FarmInputLogResponse retryPending(@PathVariable Long inputId,
                                             @AuthenticationPrincipal CustomUserDetails principal) {
        return service.retryPendingUse(principal.getFarmId(), principal.getId(), inputId);
    }
}
