package com.poultryprophet.sync;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.poultryprophet.common.BadRequestException;
import com.poultryprophet.finance.dto.CorrectExpenseRequest;
import com.poultryprophet.finance.dto.CreateFinancialTransactionRequest;
import com.poultryprophet.inventory.dto.CreateFarmProductRequest;
import com.poultryprophet.inventory.dto.InventoryAdjustmentRequest;
import com.poultryprophet.inventory.dto.StockInRequest;
import com.poultryprophet.security.CustomUserDetails;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Resolves a single original manager intent without guessing its outcome. */
@RestController
@RequestMapping("/api/manager-operations")
@PreAuthorize("hasRole('MANAGER')")
public class ManagerOperationResolutionController {
    public record Request(@NotBlank String kind, @NotBlank String targetId,
                          @NotNull UUID operationId, @NotNull JsonNode payload) {}
    public record Response(CommandReceiptService.Resolution status) {}
    private record Bound(String type, String resourceType, long resourceId, JsonNode payload) {}

    private final CommandReceiptService receipts;
    private final ObjectMapper mapper;

    public ManagerOperationResolutionController(CommandReceiptService receipts, ObjectMapper mapper) {
        this.receipts = receipts;
        this.mapper = mapper;
    }

    @PostMapping("/resolve")
    public Response resolve(@Valid @RequestBody Request request, @AuthenticationPrincipal CustomUserDetails principal) {
        Long farmId = principal.getFarmId();
        if (farmId == null || farmId <= 0) throw new BadRequestException("Join a farm first");
        if (!request.payload().isObject()) throw new BadRequestException("Original request details are required");
        Bound bound = bind(request, farmId);
        var identity = new CommandReceiptService.CommandIdentity(request.operationId(), 1, farmId, principal.getId(),
                bound.type(), bound.resourceType(), bound.resourceId());
        return new Response(receipts.cancelOrObserve(identity, bound.payload()));
    }

    private Bound bind(Request request, long farmId) {
        try {
            return switch (request.kind()) {
                case "FINANCE_CREATE" -> {
                    var body = mapper.treeToValue(request.payload(), CreateFinancialTransactionRequest.class);
                    String target = body.batchId() != null ? body.batchId().toString()
                            : body.incubationCycleId() != null ? "cycle:" + body.incubationCycleId() : "farm";
                    if (!target.equals(request.targetId())) throw new BadRequestException("Request target does not match its details");
                    yield new Bound("FINANCE_CREATE", "FARM", farmId, mapper.valueToTree(body));
                }
                case "PRODUCT_CREATE" -> {
                    if (!"farm".equals(request.targetId())) throw new BadRequestException("Request target does not match its details");
                    var body = mapper.treeToValue(request.payload(), CreateFarmProductRequest.class);
                    yield new Bound("INVENTORY_PRODUCT_CREATE", "FARM", farmId, mapper.valueToTree(body));
                }
                case "STOCK_IN" -> {
                    long productId = positiveId(request.targetId());
                    var body = mapper.treeToValue(withOperationId(request), StockInRequest.class);
                    yield new Bound("INVENTORY_STOCK_IN", "FARM_PRODUCT", productId, mapper.valueToTree(body));
                }
                case "STOCK_ADJUSTMENT" -> {
                    long productId = positiveId(request.targetId());
                    var body = mapper.treeToValue(withOperationId(request), InventoryAdjustmentRequest.class);
                    yield new Bound("INVENTORY_ADJUSTMENT", "FARM_PRODUCT", productId, mapper.valueToTree(body));
                }
                case "FINANCE_CORRECTION" -> {
                    long transactionId = positiveId(request.targetId());
                    var body = mapper.treeToValue(withOperationId(request), CorrectExpenseRequest.class);
                    yield new Bound("FINANCE_CORRECTION", "FINANCIAL_TRANSACTION", transactionId, mapper.valueToTree(body));
                }
                default -> throw new BadRequestException("Unsupported manager request type");
            };
        } catch (com.fasterxml.jackson.core.JsonProcessingException | IllegalArgumentException ex) {
            throw new BadRequestException("Invalid original request details");
        }
    }

    private ObjectNode withOperationId(Request request) {
        ObjectNode copy = ((ObjectNode) request.payload()).deepCopy();
        JsonNode supplied = copy.get("operationId");
        if (supplied != null && !supplied.isNull() && !request.operationId().toString().equals(supplied.asText()))
            throw new BadRequestException("Operation ID does not match the original request");
        copy.put("operationId", request.operationId().toString());
        return copy;
    }

    private long positiveId(String target) {
        long id = Long.parseLong(target);
        if (id <= 0) throw new IllegalArgumentException("ID must be positive");
        return id;
    }
}
