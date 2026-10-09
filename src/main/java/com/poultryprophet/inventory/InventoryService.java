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
    private static final int COST_SCALE = 6;
    private static final int MONEY_SCALE = 2;

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
        BigDecimal openingUnitCost = request.openingUnitCost();
        if (openingUnitCost != null && openingUnitCost.signum() < 0) {
            throw new BadRequestException("Opening unit cost cannot be negative");
        }
        if (openingUnitCost != null) openingUnitCost = openingUnitCost.setScale(COST_SCALE, RoundingMode.HALF_UP);
        if (opening.signum() > 0 && openingUnitCost != null && openingUnitCost.signum() >= 0) {
            product.setAverageUnitCost(openingUnitCost.setScale(COST_SCALE, RoundingMode.HALF_UP));
            product.setValuationStatus(openingUnitCost.signum() == 0 ? InventoryValuationStatus.FREE : InventoryValuationStatus.VALUED);
        } else {
            product.setValuationStatus(InventoryValuationStatus.UNVALUED);
        }
        product.setReorderLevel(nonNegative(request.reorderLevel(), "Reorder level cannot be negative"));
        product.setAllowFractionalQuantity(request.allowFractionalQuantity() == null || request.allowFractionalQuantity());
        product = productRepository.save(product);
        if (opening.signum() > 0) {
            BigDecimal openingValue = openingUnitCost == null ? null : opening.multiply(openingUnitCost).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
            saveMovement(farmId, product, InventoryMovementType.OPENING_BALANCE, opening,
                    Instant.now(), null, null, null, null, "Opening balance", userId, UUID.randomUUID(),
                    openingUnitCost, openingValue, product.getValuationStatus());
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
        if (request.batchId() != null) {
            throw new BadRequestException("Stock purchases are farm-wide; record product use on the batch instead");
        }
        BigDecimal purchaseUnitCost = request.purchaseUnitCost();
        BigDecimal legacyTotalCost = request.totalCost();
        if (purchaseUnitCost != null && purchaseUnitCost.signum() < 0) {
            throw new BadRequestException("Purchase price per unit cannot be negative");
        }
        if (legacyTotalCost != null && legacyTotalCost.signum() < 0) {
            throw new BadRequestException("Legacy purchase cost cannot be negative");
        }
        boolean freeOfCharge = Boolean.TRUE.equals(request.freeOfCharge());
        if (freeOfCharge && purchaseUnitCost != null && purchaseUnitCost.signum() != 0) {
            throw new BadRequestException("Free or donated stock must have a zero price per unit");
        }
        if (freeOfCharge && legacyTotalCost != null && legacyTotalCost.signum() != 0) {
            throw new BadRequestException("Free or donated stock must have a zero purchase total");
        }
        if (purchaseUnitCost != null && legacyTotalCost != null) {
            BigDecimal expectedTotal = quantity.multiply(purchaseUnitCost).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
            if (expectedTotal.compareTo(legacyTotalCost.setScale(MONEY_SCALE, RoundingMode.HALF_UP)) != 0) {
                throw new BadRequestException("Purchase total does not match quantity multiplied by price per unit");
            }
        }
        if (purchaseUnitCost == null && legacyTotalCost != null) {
            purchaseUnitCost = legacyTotalCost.divide(quantity, COST_SCALE, RoundingMode.HALF_UP);
        }
        if (freeOfCharge) {
            purchaseUnitCost = BigDecimal.ZERO.setScale(COST_SCALE, RoundingMode.HALF_UP);
        }
        if (purchaseUnitCost == null) {
            throw new BadRequestException("Enter the purchase price per " + product.getStockUnit() + " or mark the stock as free or donated");
        }
        if (!freeOfCharge && purchaseUnitCost.signum() <= 0) {
            throw new BadRequestException("Enter a purchase price greater than zero per " + product.getStockUnit());
        }
        if (product.getStockOnHand().signum() > 0 && product.getAverageUnitCost() == null) {
            throw new BadRequestException("Existing stock has no recorded unit cost. Set its opening cost before adding more stock");
        }
        BigDecimal unitCost = purchaseUnitCost.setScale(COST_SCALE, RoundingMode.HALF_UP);
        BigDecimal totalCost = quantity.multiply(unitCost).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        Long financialTransactionId = null;
        InventoryValuationStatus costStatus = freeOfCharge ? InventoryValuationStatus.FREE : InventoryValuationStatus.VALUED;
        BigDecimal oldQuantity = product.getStockOnHand();
        updateAverageCost(product, oldQuantity, quantity, unitCost, costStatus);
        if (costStatus != InventoryValuationStatus.FREE) {
            FinancialTransactionResponse transaction = financeService.create(farmId, userId,
                    new CreateFinancialTransactionRequest(
                            null, null, request.stockDate() == null ? LocalDate.now() : request.stockDate(),
                            FinanceTransactionType.EXPENSE, product.getProductType().name(), totalCost,
                            "PHP", trim(request.supplier()), trim(request.notes()), "INVENTORY_PURCHASE", operationId));
            financialTransactionId = transaction.id();
        }
        BigDecimal valueDelta = totalCost.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        InventoryMovement movement = saveMovement(farmId, product, InventoryMovementType.STOCK_IN, quantity,
                dateInstant(request.stockDate()), null, null, financialTransactionId, null,
                trim(request.notes()), userId, operationId, unitCost, valueDelta, costStatus);
        return InventoryMovementResponse.from(movement, product);
    }

    @Transactional
    public InventoryUseResult applyUsage(Long farmId, Long userId, Long productId, BigDecimal requestedQuantity,
                                         Instant occurredAt, Long batchId, Long inputLogId) {
        return applyUsage(farmId, userId, productId, requestedQuantity, occurredAt, batchId, inputLogId, UUID.randomUUID());
    }

    @Transactional
    public InventoryUseResult applyUsage(Long farmId, Long userId, Long productId, BigDecimal requestedQuantity,
                                         Instant occurredAt, Long batchId, Long inputLogId, UUID operationId) {
        requireFarm(farmId);
        if (productId == null || requestedQuantity == null || requestedQuantity.signum() <= 0) {
            return new InventoryUseResult(null, InventoryStatus.UNTRACKED.name(), null, null, null, null, null, null, null,
                    "Product use was saved without a stock deduction.");
        }
        FarmProduct product = productRepository.findByIdAndFarmIdForUpdate(productId, farmId).orElse(null);
        if (product == null || !product.isActive()) {
            return new InventoryUseResult(null, InventoryStatus.UNTRACKED.name(), null, null, null, null, null, null, null,
                    "The product is not available in the current farm catalog.");
        }
        if (batchId != null) batchService.requireWritableBatch(batchId, farmId);
        if (movementRepository.existsByFarmIdAndFarmInputLogId(farmId, inputLogId)) {
            InventoryMovement existing = movementRepository.findFirstByFarmIdAndFarmInputLogId(farmId, inputLogId).orElse(null);
            return existingResult(existing, product, "This product use was already deducted.");
        }
        BigDecimal quantity = requestedQuantity.setScale(SCALE, RoundingMode.HALF_UP);
        if (!product.isAllowFractionalQuantity() && quantity.stripTrailingZeros().scale() > 0) {
            return new InventoryUseResult(null, InventoryStatus.PENDING_STOCK_REVIEW.name(), product.getStockOnHand(), product.getStockOnHand(), quantity, product.getStockUnit(),
                    product.getAverageUnitCost(), null, product.getValuationStatus() == null ? null : product.getValuationStatus().name(),
                    "The product only accepts whole units; manager review is needed.");
        }
        if (product.getStockOnHand().compareTo(quantity) < 0) {
            return new InventoryUseResult(null, InventoryStatus.PENDING_STOCK_REVIEW.name(), product.getStockOnHand(), product.getStockOnHand(), quantity, product.getStockUnit(),
                    product.getAverageUnitCost(), null, product.getValuationStatus() == null ? null : product.getValuationStatus().name(),
                    "Use saved, but available stock is not enough. Manager review is needed.");
        }
        BigDecimal balanceBefore = product.getStockOnHand();
        BigDecimal unitCost = product.getAverageUnitCost();
        InventoryValuationStatus costStatus = product.getValuationStatus() == null ? InventoryValuationStatus.UNVALUED : product.getValuationStatus();
        BigDecimal batchCost = unitCost == null ? null : quantity.multiply(unitCost).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        InventoryMovement movement = saveMovement(farmId, product, InventoryMovementType.USAGE, quantity.negate(),
                occurredAt == null ? Instant.now() : occurredAt, batchId, inputLogId, null, null,
                "Product used for batch", userId, operationId, unitCost,
                batchCost == null ? null : batchCost.negate(), costStatus);
        return new InventoryUseResult(movement.getId(), InventoryStatus.DEDUCTED.name(), balanceBefore, movement.getBalanceAfter(), quantity,
                product.getStockUnit(), unitCost, batchCost, costStatus.name(),
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
        BigDecimal unitCost = product.getAverageUnitCost();
        InventoryValuationStatus costStatus = product.getValuationStatus() == null ? InventoryValuationStatus.UNVALUED : product.getValuationStatus();
        BigDecimal valueDelta = unitCost == null ? null : delta.abs().multiply(unitCost).setScale(MONEY_SCALE, RoundingMode.HALF_UP).multiply(delta.signum() > 0 ? BigDecimal.ONE : BigDecimal.ONE.negate());
        return InventoryMovementResponse.from(saveMovement(farmId, product, type, delta,
                dateInstant(request.occurredOn()), null, null, null, null, required(request.reason(), "Adjustment reason is required"), userId, operationId,
                unitCost, valueDelta, costStatus), product);
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
                input.getQuantity(),
                input.getRecordedAt(), input.getBatchId(), input.getId());
        input.setInventoryMovementId(result.movementId());
        input.setInventoryStatus(InventoryStatus.valueOf(result.status()));
        input.setUnitCostSnapshot(result.unitCost());
        input.setCalculatedCost(result.batchCost());
        input.setCostStatus(result.costStatus());
        return FarmInputLogResponse.from(inputRepository.save(input));
    }

    private InventoryMovement saveMovement(Long farmId, FarmProduct product, InventoryMovementType type,
                                           BigDecimal delta, Instant occurredAt, Long batchId, Long inputLogId,
                                           Long financialId, Long reversesId, String reason, Long userId, UUID operationId,
                                           BigDecimal unitCostSnapshot, BigDecimal inventoryValueDelta, InventoryValuationStatus costStatus) {
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
        movement.setUnitCostSnapshot(unitCostSnapshot == null ? null : unitCostSnapshot.setScale(COST_SCALE, RoundingMode.HALF_UP));
        movement.setInventoryValueDelta(inventoryValueDelta == null ? null : inventoryValueDelta.setScale(MONEY_SCALE, RoundingMode.HALF_UP));
        movement.setCostStatus(costStatus);
        movement.setCostedAt(costStatus == null ? null : Instant.now());
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

    private InventoryUseResult existingResult(InventoryMovement existing, FarmProduct product, String message) {
        if (existing == null) {
            return new InventoryUseResult(null, InventoryStatus.DEDUCTED.name(), product.getStockOnHand(), product.getStockOnHand(),
                    null, product.getStockUnit(), product.getAverageUnitCost(), null,
                    product.getValuationStatus() == null ? null : product.getValuationStatus().name(), message);
        }
        BigDecimal quantity = existing.getQuantityDelta() == null ? null : existing.getQuantityDelta().abs();
        BigDecimal cost = existing.getInventoryValueDelta() == null ? null : existing.getInventoryValueDelta().abs();
        return new InventoryUseResult(existing.getId(), InventoryStatus.DEDUCTED.name(), null, existing.getBalanceAfter(), quantity,
                product.getStockUnit(), existing.getUnitCostSnapshot(), cost,
                existing.getCostStatus() == null ? null : existing.getCostStatus().name(), message);
    }

    private void updateAverageCost(FarmProduct product, BigDecimal oldQuantity, BigDecimal receivedQuantity,
                                   BigDecimal receivedUnitCost, InventoryValuationStatus receivedStatus) {
        if (receivedUnitCost == null) {
            product.setValuationStatus(InventoryValuationStatus.UNVALUED);
            product.setAverageUnitCost(null);
            product.touch();
            return;
        }
        if (oldQuantity.signum() == 0 || product.getAverageUnitCost() == null) {
            product.setAverageUnitCost(receivedUnitCost.setScale(COST_SCALE, RoundingMode.HALF_UP));
            product.setValuationStatus(receivedStatus == InventoryValuationStatus.FREE ? InventoryValuationStatus.FREE : InventoryValuationStatus.VALUED);
        } else {
            BigDecimal oldValue = oldQuantity.multiply(product.getAverageUnitCost());
            BigDecimal newValue = oldValue.add(receivedQuantity.multiply(receivedUnitCost));
            product.setAverageUnitCost(newValue.divide(oldQuantity.add(receivedQuantity), COST_SCALE, RoundingMode.HALF_UP));
            product.setValuationStatus(InventoryValuationStatus.VALUED);
        }
        product.touch();
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
