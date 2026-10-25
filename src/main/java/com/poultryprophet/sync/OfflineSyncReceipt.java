package com.poultryprophet.sync;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** Receipt for offline operations that do not have a natural row-level operation ID. */
@Entity
@Table(name = "offline_sync_receipt")
public class OfflineSyncReceipt {
    @Id
    @Column(name = "operation_id", nullable = false)
    private UUID operationId;
    @Column(name = "farm_id", nullable = false) private Long farmId;
    @Column(name = "entity_type", nullable = false, length = 64) private String entityType;
    @Column(name = "server_id") private Long serverId;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt = Instant.now();

    public UUID getOperationId() { return operationId; }
    public void setOperationId(UUID operationId) { this.operationId = operationId; }
    public Long getFarmId() { return farmId; }
    public void setFarmId(Long farmId) { this.farmId = farmId; }
    public String getEntityType() { return entityType; }
    public void setEntityType(String entityType) { this.entityType = entityType; }
    public Long getServerId() { return serverId; }
    public void setServerId(Long serverId) { this.serverId = serverId; }
    public Instant getCreatedAt() { return createdAt; }
}
