package com.poultryprophet.inventory.dto;

import com.poultryprophet.inventory.InventoryMovement;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record InventoryMovementResponse(
        Long id, Long farmProductId, String movementType, BigDecimal quantityDelta, BigDecimal balanceAfter,
        Instant occurredAt, Long batchId, Long farmInputLogId, Long financialTransactionId,
        Long reversesMovementId, String reason, Long recordedBy, UUID operationId
) {
    public static InventoryMovementResponse from(InventoryMovement value) {
        return new InventoryMovementResponse(value.getId(), value.getFarmProductId(), value.getMovementType().name(),
                value.getQuantityDelta(), value.getBalanceAfter(), value.getOccurredAt(), value.getBatchId(),
                value.getFarmInputLogId(), value.getFinancialTransactionId(), value.getReversesMovementId(),
                value.getReason(), value.getRecordedBy(), value.getOperationId());
    }
}
