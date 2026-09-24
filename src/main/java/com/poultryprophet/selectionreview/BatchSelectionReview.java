package com.poultryprophet.selectionreview;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Immutable report snapshot after finalization. Source records can change later, but a manager's
 * reviewed report remains auditable and reproducible.
 */
@Entity
@Table(name = "batch_selection_review", indexes = {
        @Index(name = "idx_selection_review_farm_batch", columnList = "farm_id,batch_id"),
        @Index(name = "idx_selection_review_generated_at", columnList = "generated_at")
})
public class BatchSelectionReview {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "farm_id", nullable = false)
    private Long farmId;

    @Column(name = "batch_id", nullable = false)
    private Long batchId;

    @Column(name = "period_start", nullable = false)
    private LocalDate periodStart;

    @Column(name = "period_end", nullable = false)
    private LocalDate periodEnd;

    @Column(name = "as_of_date", nullable = false)
    private LocalDate asOfDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SelectionReviewStatus status = SelectionReviewStatus.DRAFT;

    @Enumerated(EnumType.STRING)
    @Column(name = "review_status", nullable = false)
    private ManagerReviewStatus reviewStatus = ManagerReviewStatus.NOT_REVIEWED;

    @Column(name = "manager_notes", columnDefinition = "text")
    private String managerNotes;

    @Column(name = "next_review_date")
    private LocalDate nextReviewDate;

    @Column(name = "payload_version", nullable = false)
    private String payloadVersion;

    /** User-facing snapshot sequence. Nullable for rows created before versioning was added. */
    @Column(name = "version_number")
    private Integer versionNumber;

    /** Why the manager saved this report, e.g. routine review or selection review. */
    @Column
    private String purpose;

    /** Optional context entered when the snapshot is generated. */
    @Column(name = "snapshot_note", columnDefinition = "text")
    private String snapshotNote;

    /** Source-data cutoff used to explain when this immutable snapshot was produced. */
    @Column(name = "source_cutoff_at")
    private Instant sourceCutoffAt;

    /** Client retry identity. Nullable for legacy rows and older clients. */
    @Column(name = "idempotency_key")
    private String idempotencyKey;

    @Column(name = "payload_json", nullable = false, columnDefinition = "text")
    private String payloadJson;

    @Column(name = "generated_by", nullable = false)
    private Long generatedBy;

    @Column(name = "generated_at", nullable = false, updatable = false)
    private Instant generatedAt = Instant.now();

    private Long reviewedBy;
    private Instant reviewedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getFarmId() { return farmId; }
    public void setFarmId(Long farmId) { this.farmId = farmId; }
    public Long getBatchId() { return batchId; }
    public void setBatchId(Long batchId) { this.batchId = batchId; }
    public LocalDate getPeriodStart() { return periodStart; }
    public void setPeriodStart(LocalDate periodStart) { this.periodStart = periodStart; }
    public LocalDate getPeriodEnd() { return periodEnd; }
    public void setPeriodEnd(LocalDate periodEnd) { this.periodEnd = periodEnd; }
    public LocalDate getAsOfDate() { return asOfDate; }
    public void setAsOfDate(LocalDate asOfDate) { this.asOfDate = asOfDate; }
    public SelectionReviewStatus getStatus() { return status; }
    public void setStatus(SelectionReviewStatus status) { this.status = status; }
    public ManagerReviewStatus getReviewStatus() { return reviewStatus; }
    public void setReviewStatus(ManagerReviewStatus reviewStatus) { this.reviewStatus = reviewStatus; }
    public String getManagerNotes() { return managerNotes; }
    public void setManagerNotes(String managerNotes) { this.managerNotes = managerNotes; }
    public LocalDate getNextReviewDate() { return nextReviewDate; }
    public void setNextReviewDate(LocalDate nextReviewDate) { this.nextReviewDate = nextReviewDate; }
    public String getPayloadVersion() { return payloadVersion; }
    public void setPayloadVersion(String payloadVersion) { this.payloadVersion = payloadVersion; }
    public Integer getVersionNumber() { return versionNumber; }
    public void setVersionNumber(Integer versionNumber) { this.versionNumber = versionNumber; }
    public String getPurpose() { return purpose; }
    public void setPurpose(String purpose) { this.purpose = purpose; }
    public String getSnapshotNote() { return snapshotNote; }
    public void setSnapshotNote(String snapshotNote) { this.snapshotNote = snapshotNote; }
    public Instant getSourceCutoffAt() { return sourceCutoffAt; }
    public void setSourceCutoffAt(Instant sourceCutoffAt) { this.sourceCutoffAt = sourceCutoffAt; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }
    public String getPayloadJson() { return payloadJson; }
    public void setPayloadJson(String payloadJson) { this.payloadJson = payloadJson; }
    public Long getGeneratedBy() { return generatedBy; }
    public void setGeneratedBy(Long generatedBy) { this.generatedBy = generatedBy; }
    public Instant getGeneratedAt() { return generatedAt; }
    public void setGeneratedAt(Instant generatedAt) { this.generatedAt = generatedAt; }
    public Long getReviewedBy() { return reviewedBy; }
    public void setReviewedBy(Long reviewedBy) { this.reviewedBy = reviewedBy; }
    public Instant getReviewedAt() { return reviewedAt; }
    public void setReviewedAt(Instant reviewedAt) { this.reviewedAt = reviewedAt; }
}
