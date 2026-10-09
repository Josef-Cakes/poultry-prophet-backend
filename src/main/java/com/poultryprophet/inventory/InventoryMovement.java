package com.poultryprophet.inventory;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "inventory_movement", indexes = {
        @Index(name = "idx_inventory_movement_product_date", columnList = "farm_product_id,occurred_at"),
        @Index(name = "idx_inventory_movement_batch_date", columnList = "batch_id,occurred_at")
})
public class InventoryMovement {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "farm_id", nullable = false) private Long farmId;
    @Column(name = "farm_product_id", nullable = false) private Long farmProductId;
    @Enumerated(EnumType.STRING) @Column(name = "movement_type", nullable = false, length = 32) private InventoryMovementType movementType;
    @Column(name = "quantity_delta", nullable = false, precision = 14, scale = 3) private BigDecimal quantityDelta;
    @Column(name = "balance_after", nullable = false, precision = 14, scale = 3) private BigDecimal balanceAfter;
    @Column(name = "unit_cost_snapshot", precision = 16, scale = 6) private BigDecimal unitCostSnapshot;
    @Column(name = "inventory_value_delta", precision = 16, scale = 2) private BigDecimal inventoryValueDelta;
    @Enumerated(EnumType.STRING) @Column(name = "cost_status", length = 16)
    private InventoryValuationStatus costStatus;
    @Column(name = "costed_at") private Instant costedAt;
    @Column(nullable = false) private Instant occurredAt;
    @Column(name = "batch_id") private Long batchId;
    @Column(name = "farm_input_log_id") private Long farmInputLogId;
    @Column(name = "financial_transaction_id") private Long financialTransactionId;
    @Column(name = "reverses_movement_id") private Long reversesMovementId;
    @Column(columnDefinition = "text") private String reason;
    @Column(name = "recorded_by", nullable = false) private Long recordedBy;
    @Column(name = "operation_id", unique = true) private UUID operationId;
    @Column(nullable = false, updatable = false) private Instant createdAt = Instant.now();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getFarmId() { return farmId; }
    public void setFarmId(Long farmId) { this.farmId = farmId; }
    public Long getFarmProductId() { return farmProductId; }
    public void setFarmProductId(Long farmProductId) { this.farmProductId = farmProductId; }
    public InventoryMovementType getMovementType() { return movementType; }
    public void setMovementType(InventoryMovementType movementType) { this.movementType = movementType; }
    public BigDecimal getQuantityDelta() { return quantityDelta; }
    public void setQuantityDelta(BigDecimal quantityDelta) { this.quantityDelta = quantityDelta; }
    public BigDecimal getBalanceAfter() { return balanceAfter; }
    public void setBalanceAfter(BigDecimal balanceAfter) { this.balanceAfter = balanceAfter; }
    public BigDecimal getUnitCostSnapshot() { return unitCostSnapshot; }
    public void setUnitCostSnapshot(BigDecimal unitCostSnapshot) { this.unitCostSnapshot = unitCostSnapshot; }
    public BigDecimal getInventoryValueDelta() { return inventoryValueDelta; }
    public void setInventoryValueDelta(BigDecimal inventoryValueDelta) { this.inventoryValueDelta = inventoryValueDelta; }
    public InventoryValuationStatus getCostStatus() { return costStatus; }
    public void setCostStatus(InventoryValuationStatus costStatus) { this.costStatus = costStatus; }
    public Instant getCostedAt() { return costedAt; }
    public void setCostedAt(Instant costedAt) { this.costedAt = costedAt; }
    public Instant getOccurredAt() { return occurredAt; }
    public void setOccurredAt(Instant occurredAt) { this.occurredAt = occurredAt; }
    public Long getBatchId() { return batchId; }
    public void setBatchId(Long batchId) { this.batchId = batchId; }
    public Long getFarmInputLogId() { return farmInputLogId; }
    public void setFarmInputLogId(Long farmInputLogId) { this.farmInputLogId = farmInputLogId; }
    public Long getFinancialTransactionId() { return financialTransactionId; }
    public void setFinancialTransactionId(Long financialTransactionId) { this.financialTransactionId = financialTransactionId; }
    public Long getReversesMovementId() { return reversesMovementId; }
    public void setReversesMovementId(Long reversesMovementId) { this.reversesMovementId = reversesMovementId; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public Long getRecordedBy() { return recordedBy; }
    public void setRecordedBy(Long recordedBy) { this.recordedBy = recordedBy; }
    public UUID getOperationId() { return operationId; }
    public void setOperationId(UUID operationId) { this.operationId = operationId; }
    public Instant getCreatedAt() { return createdAt; }
}
