package com.poultryprophet.inventory.dto;

import com.poultryprophet.inventory.InventoryMovement;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record InventoryMovementResponse(
        Long id, Long farmProductId, String movementType, BigDecimal quantityDelta, BigDecimal balanceAfter,
        BigDecimal unitCostSnapshot, BigDecimal inventoryValueDelta, String costStatus, java.time.Instant costedAt,
        Instant occurredAt, Long batchId, Long farmInputLogId, Long financialTransactionId,
        Long reversesMovementId, String reason, Long recordedBy, UUID operationId,
        BigDecimal calculatedTotal, BigDecimal averageUnitCostAfter, BigDecimal inventoryValueAfter
) {
    public static InventoryMovementResponse from(InventoryMovement value) {
        return new InventoryMovementResponse(value.getId(), value.getFarmProductId(), value.getMovementType().name(),
                value.getQuantityDelta(), value.getBalanceAfter(), value.getUnitCostSnapshot(), value.getInventoryValueDelta(),
                value.getCostStatus() == null ? null : value.getCostStatus().name(), value.getCostedAt(), value.getOccurredAt(), value.getBatchId(),
                value.getFarmInputLogId(), value.getFinancialTransactionId(), value.getReversesMovementId(),
                value.getReason(), value.getRecordedBy(), value.getOperationId(), calculatedTotal(value), null, null);
    }

    public static InventoryMovementResponse from(InventoryMovement value, com.poultryprophet.inventory.FarmProduct product) {
        BigDecimal averageUnitCost = product == null ? null : product.getAverageUnitCost();
        BigDecimal inventoryValue = product == null || averageUnitCost == null ? null
                : product.getStockOnHand().multiply(averageUnitCost).setScale(2, java.math.RoundingMode.HALF_UP);
        return new InventoryMovementResponse(value.getId(), value.getFarmProductId(), value.getMovementType().name(),
                value.getQuantityDelta(), value.getBalanceAfter(), value.getUnitCostSnapshot(), value.getInventoryValueDelta(),
                value.getCostStatus() == null ? null : value.getCostStatus().name(), value.getCostedAt(), value.getOccurredAt(), value.getBatchId(),
                value.getFarmInputLogId(), value.getFinancialTransactionId(), value.getReversesMovementId(),
                value.getReason(), value.getRecordedBy(), value.getOperationId(), calculatedTotal(value), averageUnitCost, inventoryValue);
    }

    private static BigDecimal calculatedTotal(InventoryMovement value) {
        return value.getInventoryValueDelta() == null ? null : value.getInventoryValueDelta().abs();
    }
}
