package com.poultryprophet.batch;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "batch_lifecycle_audit")
public class BatchLifecycleAudit {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "farm_id", nullable = false) private Long farmId;
    @Column(name = "batch_id", nullable = false) private Long batchId;
    @Column(name = "batch_name_snapshot", nullable = false) private String batchNameSnapshot;
    @Column(nullable = false, length = 32) private String action;
    @Enumerated(EnumType.STRING) @Column(name = "previous_status") private BatchStatus previousStatus;
    @Enumerated(EnumType.STRING) @Column(name = "new_status") private BatchStatus newStatus;
    @Column(length = 500) private String reason;
    @Column(name = "performed_by_user_id") private Long performedByUserId;
    @Column(name = "performed_at", nullable = false) private Instant performedAt = Instant.now();
    @Column(name = "metadata_json", columnDefinition = "text") private String metadataJson;

    public Long getId() { return id; }
    public Long getFarmId() { return farmId; }
    public void setFarmId(Long farmId) { this.farmId = farmId; }
    public Long getBatchId() { return batchId; }
    public void setBatchId(Long batchId) { this.batchId = batchId; }
    public String getBatchNameSnapshot() { return batchNameSnapshot; }
    public void setBatchNameSnapshot(String batchNameSnapshot) { this.batchNameSnapshot = batchNameSnapshot; }
    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
    public BatchStatus getPreviousStatus() { return previousStatus; }
    public void setPreviousStatus(BatchStatus previousStatus) { this.previousStatus = previousStatus; }
    public BatchStatus getNewStatus() { return newStatus; }
    public void setNewStatus(BatchStatus newStatus) { this.newStatus = newStatus; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public Long getPerformedByUserId() { return performedByUserId; }
    public void setPerformedByUserId(Long performedByUserId) { this.performedByUserId = performedByUserId; }
    public Instant getPerformedAt() { return performedAt; }
    public void setPerformedAt(Instant performedAt) { this.performedAt = performedAt; }
    public String getMetadataJson() { return metadataJson; }
    public void setMetadataJson(String metadataJson) { this.metadataJson = metadataJson; }
}
