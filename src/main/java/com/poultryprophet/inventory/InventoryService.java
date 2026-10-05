package com.poultryprophet.inventory;

import com.poultryprophet.batch.BatchService;
import com.poultryprophet.common.BadRequestException;
import com.poultryprophet.common.NotFoundException;
import com.poultryprophet.finance.FinanceService;
import com.poultryprophet.finance.FinanceTransactionType;
import com.poultryprophet.finance.dto.CreateFinancialTransactionRequest;
import com.poultryprophet.finance.dto.FinancialTransactionResponse;
import com.poultryprophet.input.InputProductType;
import com.poultryprophet.input.FarmInputLog;
import com.poultryprophet.input.FarmInputLogRepository;
import com.poultryprophet.input.dto.FarmInputLogResponse;
import com.poultryprophet.inventory.dto.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
public class InventoryService {
    private static final int SCALE = 3;

    private final FarmProductRepository productRepository;
    private final InventoryMovementRepository movementRepository;
    private final BatchService batchService;
    private final FinanceService financeService;
    private final FarmInputLogRepository inputRepository;

    public InventoryService(FarmProductRepository productRepository,
                            InventoryMovementRepository movementRepository,
                            BatchService batchService,
                            FinanceService financeService) {
        this(productRepository, movementRepository, batchService, financeService, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public InventoryService(FarmProductRepository productRepository,
                            InventoryMovementRepository movementRepository,
                            BatchService batchService,
                            FinanceService financeService,
                            FarmInputLogRepository inputRepository) {
        this.productRepository = productRepository;
        this.movementRepository = movementRepository;
        this.batchService = batchService;
        this.financeService = financeService;
        this.inputRepository = inputRepository;
    }

    @Transactional(readOnly = true)
    public List<FarmProductResponse> listProducts(Long farmId, boolean includeInactive) {
        if (farmId == null) throw new BadRequestException("Join a farm before viewing products");
        List<FarmProduct> products = includeInactive
                ? productRepository.findByFarmIdOrderByActiveDescBrandNameAsc(farmId)
                : productRepository.findByFarmIdAndActiveOrderByBrandNameAsc(farmId, true);
        return products.stream().map(FarmProductResponse::from).toList();
    }

    @Transactional
    public FarmProductResponse createProduct(Long farmId, Long userId, CreateFarmProductRequest request) {
        requireFarm(farmId);
        String brand = required(request.brandName(), "Product or brand name is required");
        String unit = normalizeUnit(request.stockUnit());
        BigDecimal opening = normalize(request.openingQuantity());
        FarmProduct product = new FarmProduct();
        product.setFarmId(farmId);
        product.setCreatedBy(userId);
        product.setProductType(request.productType());
        product.setBrandName(brand);
        product.setProductName(trim(request.productName()));
        product.setPackageDescription(trim(request.packageDescription()));
        product.setStockUnit(unit);
        product.setStockOnHand(opening);
        product.setReorderLevel(nonNegative(request.reorderLevel(), "Reorder level cannot be negative"));
        product.setAllowFractionalQuantity(request.allowFractionalQuantity() == null || request.allowFractionalQuantity());
        product = productRepository.save(product);
        if (opening.signum() > 0) {
            saveMovement(farmId, product, InventoryMovementType.OPENING_BALANCE, opening,
                    Instant.now(), null, null, null, null, "Opening balance", userId, UUID.randomUUID());
        }
        return FarmProductResponse.from(product);
    }

    @Transactional
    public InventoryMovementResponse stockIn(Long farmId, Long userId, Long productId, StockInRequest request) {
        requireFarm(farmId);
        UUID operationId = request.operationId() == null ? UUID.randomUUID() : request.operationId();
        InventoryMovement duplicate = movementRepository.findByOperationId(operationId).orElse(null);
        if (duplicate != null) return InventoryMovementResponse.from(duplicate);
        BigDecimal quantity = positive(request.quantity(), "Stock quantity must be greater than zero");
        FarmProduct product = productRepository.findByIdAndFarmIdForUpdate(productId, farmId)
                .orElseThrow(() -> new NotFoundException("Product not found"));
        Long batchId = request.batchId();
        if (batchId != null) batchService.requireBatch(batchId, farmId);
        BigDecimal totalCost = request.totalCost();
        if (totalCost != null && totalCost.signum() < 0) throw new BadRequestException("Purchase cost cannot be negative");
        boolean recordExpense = Boolean.TRUE.equals(request.recordExpense());
        if (recordExpense && (totalCost == null || totalCost.signum() <= 0)) {
            throw new BadRequestException("Enter a purchase amount before recording an expense");
        }
        Long financialTransactionId = null;
        if (recordExpense) {
            FinancialTransactionResponse transaction = financeService.create(farmId, userId,
                    new CreateFinancialTransactionRequest(
                            batchId, null, request.stockDate() == null ? LocalDate.now() : request.stockDate(),
                            FinanceTransactionType.EXPENSE, product.getProductType().name(), totalCost,
                            "PHP", trim(request.supplier()), trim(request.notes())));
            financialTransactionId = transaction.id();
        }
        InventoryMovement movement = saveMovement(farmId, product, InventoryMovementType.STOCK_IN, quantity,
                dateInstant(request.stockDate()), batchId, null, financialTransactionId, null,
                trim(request.notes()), userId, operationId);
        return InventoryMovementResponse.from(movement);
    }

    @Transactional
    public InventoryUseResult applyUsage(Long farmId, Long userId, Long productId, BigDecimal requestedQuantity,
                                         Instant occurredAt, Long batchId, Long inputLogId) {
        requireFarm(farmId);
        if (productId == null || requestedQuantity == null || requestedQuantity.signum() <= 0) {
            return new InventoryUseResult(null, InventoryStatus.UNTRACKED.name(), null,
                    "Product use was saved without a stock deduction.");
        }
        FarmProduct product = productRepository.findByIdAndFarmIdForUpdate(productId, farmId).orElse(null);
        if (product == null || !product.isActive()) {
            return new InventoryUseResult(null, InventoryStatus.UNTRACKED.name(), null,
                    "The product is not available in the current farm catalog.");
        }
        if (batchId != null) batchService.requireBatch(batchId, farmId);
        if (movementRepository.existsByFarmIdAndFarmInputLogId(farmId, inputLogId)) {
            InventoryMovement existing = movementRepository.findByFarmIdAndBatchIdOrderByOccurredAtDescCreatedAtDesc(farmId, batchId)
                    .stream().filter(item -> Objects.equals(item.getFarmInputLogId(), inputLogId)).findFirst().orElse(null);
            return new InventoryUseResult(existing == null ? null : existing.getId(), InventoryStatus.DEDUCTED.name(),
                    existing == null ? product.getStockOnHand() : existing.getBalanceAfter(), "This product use was already deducted.");
        }
        BigDecimal quantity = requestedQuantity.setScale(SCALE, RoundingMode.HALF_UP);
        if (!product.isAllowFractionalQuantity() && quantity.stripTrailingZeros().scale() > 0) {
            return new InventoryUseResult(null, InventoryStatus.PENDING_STOCK_REVIEW.name(), product.getStockOnHand(),
                    "The product only accepts whole units; manager review is needed.");
        }
        if (product.getStockOnHand().compareTo(quantity) < 0) {
            return new InventoryUseResult(null, InventoryStatus.PENDING_STOCK_REVIEW.name(), product.getStockOnHand(),
                    "Use saved, but available stock is not enough. Manager review is needed.");
        }
        InventoryMovement movement = saveMovement(farmId, product, InventoryMovementType.USAGE, quantity.negate(),
                occurredAt == null ? Instant.now() : occurredAt, batchId, inputLogId, null, null,
                "Product used for batch", userId, UUID.randomUUID());
        return new InventoryUseResult(movement.getId(), InventoryStatus.DEDUCTED.name(), movement.getBalanceAfter(),
                "Product use was deducted from stock.");
    }

    @Transactional
    public InventoryMovementResponse adjust(Long farmId, Long userId, Long productId, InventoryAdjustmentRequest request) {
        requireFarm(farmId);
        UUID operationId = request.operationId() == null ? UUID.randomUUID() : request.operationId();
        InventoryMovement duplicate = movementRepository.findByOperationId(operationId).orElse(null);
        if (duplicate != null) return InventoryMovementResponse.from(duplicate);
        BigDecimal delta = normalize(request.quantityDelta());
        if (delta.signum() == 0) throw new BadRequestException("Adjustment cannot be zero");
        FarmProduct product = productRepository.findByIdAndFarmIdForUpdate(productId, farmId)
                .orElseThrow(() -> new NotFoundException("Product not found"));
        if (product.getStockOnHand().add(delta).signum() < 0) throw new BadRequestException("Adjustment cannot make stock negative");
        InventoryMovementType type = delta.signum() > 0 ? InventoryMovementType.ADJUSTMENT_IN : InventoryMovementType.ADJUSTMENT_OUT;
        return InventoryMovementResponse.from(saveMovement(farmId, product, type, delta,
                dateInstant(request.occurredOn()), null, null, null, null, required(request.reason(), "Adjustment reason is required"), userId, operationId));
    }

    @Transactional(readOnly = true)
    public List<InventoryMovementResponse> movements(Long farmId, Long productId, Long batchId) {
        requireFarm(farmId);
        if (productId != null) {
            return movementRepository.findByFarmIdAndFarmProductIdOrderByOccurredAtDescCreatedAtDesc(farmId, productId)
                    .stream().map(InventoryMovementResponse::from).toList();
        }
        if (batchId != null) {
            batchService.requireBatch(batchId, farmId);
            return movementRepository.findByFarmIdAndBatchIdOrderByOccurredAtDescCreatedAtDesc(farmId, batchId)
                    .stream().map(InventoryMovementResponse::from).toList();
        }
        return movementRepository.findAll().stream().filter(value -> farmId.equals(value.getFarmId()))
                .sorted((a, b) -> b.getOccurredAt().compareTo(a.getOccurredAt()))
                .map(InventoryMovementResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<FarmInputLogResponse> pendingUses(Long farmId) {
        requireFarm(farmId);
        if (inputRepository == null) return List.of();
        return inputRepository.findByFarmIdAndInventoryStatusOrderByRecordedAtDesc(farmId, InventoryStatus.PENDING_STOCK_REVIEW)
                .stream().map(FarmInputLogResponse::from).toList();
    }

    @Transactional
    public FarmInputLogResponse retryPendingUse(Long farmId, Long userId, Long inputId) {
        requireFarm(farmId);
        if (inputRepository == null) throw new BadRequestException("Pending stock review is unavailable");
        FarmInputLog input = inputRepository.findByIdAndFarmId(inputId, farmId)
                .orElseThrow(() -> new NotFoundException("Product-use record not found"));
        if (input.getInventoryStatus() != InventoryStatus.PENDING_STOCK_REVIEW) {
            throw new BadRequestException("This product-use record does not need stock review");
        }
        InventoryUseResult result = applyUsage(farmId, userId, input.getFarmProductId(),
                input.getQuantity() == null ? null : BigDecimal.valueOf(input.getQuantity()),
                input.getRecordedAt(), input.getBatchId(), input.getId());
        input.setInventoryMovementId(result.movementId());
        input.setInventoryStatus(InventoryStatus.valueOf(result.status()));
        return FarmInputLogResponse.from(inputRepository.save(input));
    }

    private InventoryMovement saveMovement(Long farmId, FarmProduct product, InventoryMovementType type,
                                           BigDecimal delta, Instant occurredAt, Long batchId, Long inputLogId,
                                           Long financialId, Long reversesId, String reason, Long userId, UUID operationId) {
        BigDecimal balance = product.getStockOnHand().add(delta).setScale(SCALE, RoundingMode.HALF_UP);
        if (balance.signum() < 0) throw new BadRequestException("Stock cannot become negative");
        product.setStockOnHand(balance);
        product.touch();
        productRepository.save(product);
        InventoryMovement movement = new InventoryMovement();
        movement.setFarmId(farmId);
        movement.setFarmProductId(product.getId());
        movement.setMovementType(type);
        movement.setQuantityDelta(delta.setScale(SCALE, RoundingMode.HALF_UP));
        movement.setBalanceAfter(balance);
        movement.setOccurredAt(occurredAt);
        movement.setBatchId(batchId);
        movement.setFarmInputLogId(inputLogId);
        movement.setFinancialTransactionId(financialId);
        movement.setReversesMovementId(reversesId);
        movement.setReason(reason);
        movement.setRecordedBy(userId);
        movement.setOperationId(operationId);
        return movementRepository.save(movement);
    }

    private void requireFarm(Long farmId) { if (farmId == null) throw new BadRequestException("Join a farm first"); }
    private BigDecimal positive(BigDecimal value, String message) { if (value == null || value.signum() <= 0) throw new BadRequestException(message); return normalize(value); }
    private BigDecimal nonNegative(BigDecimal value, String message) { if (value != null && value.signum() < 0) throw new BadRequestException(message); return normalize(value); }
    private BigDecimal normalize(BigDecimal value) { return value == null ? BigDecimal.ZERO.setScale(SCALE) : value.setScale(SCALE, RoundingMode.HALF_UP); }
    private String required(String value, String message) { String clean = trim(value); if (clean == null) throw new BadRequestException(message); return clean; }
    private String trim(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private String normalizeUnit(String value) { return required(value, "Stock unit is required").toLowerCase(); }
    private Instant dateInstant(LocalDate date) { return (date == null ? LocalDate.now() : date).atStartOfDay(java.time.ZoneOffset.UTC).toInstant(); }
}
