package com.poultryprophet.inventory;

import com.poultryprophet.inventory.dto.*;
import com.poultryprophet.common.PageResponse;
import com.poultryprophet.input.dto.FarmInputLogResponse;
import com.poultryprophet.security.CustomUserDetails;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.poultryprophet.sync.CommandReceiptService;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/inventory")
public class InventoryController {
    private final InventoryService service;
    private final CommandReceiptService receipts;
    private final ObjectMapper mapper;

    public InventoryController(InventoryService service, CommandReceiptService receipts, ObjectMapper mapper) {
        this.service = service; this.receipts = receipts; this.mapper = mapper;
    }

    @GetMapping("/products")
    public List<FarmProductResponse> products(@RequestParam(defaultValue = "false") boolean includeInactive,
                                              @AuthenticationPrincipal CustomUserDetails principal) {
        return service.listProducts(principal.getFarmId(), includeInactive).stream()
                .map(product -> product.forRole(principal.getUser().getRole())).toList();
    }

    @PostMapping("/products")
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseEntity<FarmProductResponse> create(@Valid @RequestBody CreateFarmProductRequest request,
                                                      @RequestHeader(value = "Idempotency-Key", required = false) UUID operationId,
                                                      @AuthenticationPrincipal CustomUserDetails principal) {
        FarmProductResponse saved;
        if (operationId == null) {
            saved = service.createProduct(principal.getFarmId(), principal.getId(), request);
        } else {
            var identity = new CommandReceiptService.CommandIdentity(operationId, 1, principal.getFarmId(), principal.getId(),
                    "INVENTORY_PRODUCT_CREATE", "FARM", principal.getFarmId());
            saved = receipts.executeOnce(identity, mapper.valueToTree(request), FarmProductResponse.class,
                    () -> service.createProduct(principal.getFarmId(), principal.getId(), request)).outcome();
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    public record ReorderLevelRequest(@jakarta.validation.constraints.DecimalMin("0.0") java.math.BigDecimal reorderLevel) {}

    @PatchMapping("/products/{productId}/reorder-level")
    @PreAuthorize("hasRole('MANAGER')")
    public FarmProductResponse setReorderLevel(@PathVariable Long productId, @Valid @RequestBody ReorderLevelRequest request,
                                                @AuthenticationPrincipal CustomUserDetails principal) {
        return service.setReorderLevel(principal.getFarmId(), productId, request.reorderLevel());
    }

    @PostMapping("/products/{productId}/stock-ins")
    @PreAuthorize("hasRole('MANAGER')")
    public InventoryMovementResponse stockIn(@PathVariable Long productId, @Valid @RequestBody StockInRequest request,
                                             @AuthenticationPrincipal CustomUserDetails principal) {
        if (request.operationId() == null) return service.stockIn(principal.getFarmId(), principal.getId(), productId, request);
        var identity = new CommandReceiptService.CommandIdentity(request.operationId(), 1, principal.getFarmId(), principal.getId(),
                "INVENTORY_STOCK_IN", "FARM_PRODUCT", productId);
        return receipts.executeOnce(identity, mapper.valueToTree(request), InventoryMovementResponse.class,
                () -> service.stockIn(principal.getFarmId(), principal.getId(), productId, request)).outcome();
    }

    @PostMapping("/products/{productId}/adjustments")
    @PreAuthorize("hasRole('MANAGER')")
    public InventoryMovementResponse adjust(@PathVariable Long productId, @Valid @RequestBody InventoryAdjustmentRequest request,
                                            @AuthenticationPrincipal CustomUserDetails principal) {
        if (request.operationId() == null) return service.adjust(principal.getFarmId(), principal.getId(), productId, request);
        var identity = new CommandReceiptService.CommandIdentity(request.operationId(), 1, principal.getFarmId(), principal.getId(),
                "INVENTORY_ADJUSTMENT", "FARM_PRODUCT", productId);
        return receipts.executeOnce(identity, mapper.valueToTree(request), InventoryMovementResponse.class,
                () -> service.adjust(principal.getFarmId(), principal.getId(), productId, request)).outcome();
    }

    @GetMapping("/movements")
    @PreAuthorize("hasRole('MANAGER')")
    public List<InventoryMovementResponse> movements(@RequestParam(required = false) Long productId,
                                                     @RequestParam(required = false) Long batchId,
                                                     @AuthenticationPrincipal CustomUserDetails principal) {
        return service.movements(principal.getFarmId(), productId, batchId);
    }

    @GetMapping("/demo-cost-impact")
    @PreAuthorize("hasRole('MANAGER')")
    public java.util.Map<String, Boolean> demoCostImpact(@RequestParam Long batchId,
            @RequestParam java.time.LocalDate start, @RequestParam java.time.LocalDate end,
            @RequestParam(required = false) java.time.Instant cutoff, @AuthenticationPrincipal CustomUserDetails principal) {
        return java.util.Map.of("mayIncludeDemoStock", service.demoStockMayAffectBatchCost(
                principal.getFarmId(), batchId, start, end, cutoff == null ? java.time.Instant.now() : cutoff));
    }

    @GetMapping("/movements/page")
    @PreAuthorize("hasRole('MANAGER')")
    public PageResponse<InventoryMovementResponse> movementsPage(@RequestParam(defaultValue = "0") int page,
                                                                 @RequestParam(defaultValue = "20") int size,
                                                                 @RequestParam(required = false) Long productId,
                                                                 @RequestParam(required = false) Long batchId,
                                                                 @AuthenticationPrincipal CustomUserDetails principal) {
        return service.movementsPage(principal.getFarmId(), productId, batchId, page, size);
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
