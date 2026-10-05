package com.poultryprophet.selectionsession;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "batch_selection_session", indexes = {
        @Index(name = "idx_selection_session_farm_batch_date", columnList = "farm_id,batch_id,selection_date"),
        @Index(name = "idx_selection_session_batch_status", columnList = "batch_id,status")
})
public class BatchSelectionSession {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "farm_id", nullable = false)
    private Long farmId;

    @Column(name = "batch_id", nullable = false)
    private Long batchId;

    @Column(name = "selection_date", nullable = false)
    private LocalDate selectionDate;

    @Column(name = "reviewer_id", nullable = false)
    private Long reviewerId;

    @Column(name = "evaluated_count", nullable = false)
    private int evaluatedCount;

    @Column(name = "accepted_count", nullable = false)
    private int acceptedCount;

    @Column(name = "continue_observation_count", nullable = false)
    private int continueObservationCount;

    @Column(name = "not_accepted_count", nullable = false)
    private int notAcceptedCount;

    @Column(name = "other_count", nullable = false)
    private int otherCount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SelectionSessionStatus status = SelectionSessionStatus.DRAFT;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "batch_selection_session_criterion",
            joinColumns = @JoinColumn(name = "selection_session_id"))
    @Column(name = "criterion_code", nullable = false)
    private Set<String> criterionCodes = new LinkedHashSet<>();

    @Column(name = "criteria_notes", columnDefinition = "text")
    private String criteriaNotes;

    @Column(name = "session_notes", columnDefinition = "text")
    private String sessionNotes;

    @Column(name = "operation_id", unique = true)
    private UUID operationId;

    @Column(name = "supersedes_session_id")
    private Long supersedesSessionId;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(nullable = false)
    private Instant updatedAt = Instant.now();

    @Column
    private Instant finalizedAt;

    @Version
    @Column(nullable = false)
    private long version;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getFarmId() { return farmId; }
    public void setFarmId(Long farmId) { this.farmId = farmId; }
    public Long getBatchId() { return batchId; }
    public void setBatchId(Long batchId) { this.batchId = batchId; }
    public LocalDate getSelectionDate() { return selectionDate; }
    public void setSelectionDate(LocalDate selectionDate) { this.selectionDate = selectionDate; }
    public Long getReviewerId() { return reviewerId; }
    public void setReviewerId(Long reviewerId) { this.reviewerId = reviewerId; }
    public int getEvaluatedCount() { return evaluatedCount; }
    public void setEvaluatedCount(int evaluatedCount) { this.evaluatedCount = evaluatedCount; }
    public int getAcceptedCount() { return acceptedCount; }
    public void setAcceptedCount(int acceptedCount) { this.acceptedCount = acceptedCount; }
    public int getContinueObservationCount() { return continueObservationCount; }
    public void setContinueObservationCount(int continueObservationCount) { this.continueObservationCount = continueObservationCount; }
    public int getNotAcceptedCount() { return notAcceptedCount; }
    public void setNotAcceptedCount(int notAcceptedCount) { this.notAcceptedCount = notAcceptedCount; }
    public int getOtherCount() { return otherCount; }
    public void setOtherCount(int otherCount) { this.otherCount = otherCount; }
    public SelectionSessionStatus getStatus() { return status; }
    public void setStatus(SelectionSessionStatus status) { this.status = status; }
    public Set<String> getCriterionCodes() { return criterionCodes; }
    public void setCriterionCodes(Set<String> criterionCodes) { this.criterionCodes = criterionCodes == null ? new LinkedHashSet<>() : new LinkedHashSet<>(criterionCodes); }
    public String getCriteriaNotes() { return criteriaNotes; }
    public void setCriteriaNotes(String criteriaNotes) { this.criteriaNotes = criteriaNotes; }
    public String getSessionNotes() { return sessionNotes; }
    public void setSessionNotes(String sessionNotes) { this.sessionNotes = sessionNotes; }
    public UUID getOperationId() { return operationId; }
    public void setOperationId(UUID operationId) { this.operationId = operationId; }
    public Long getSupersedesSessionId() { return supersedesSessionId; }
    public void setSupersedesSessionId(Long supersedesSessionId) { this.supersedesSessionId = supersedesSessionId; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
    public Instant getFinalizedAt() { return finalizedAt; }
    public void setFinalizedAt(Instant finalizedAt) { this.finalizedAt = finalizedAt; }
    public long getVersion() { return version; }
    public void setVersion(long version) { this.version = version; }
}
