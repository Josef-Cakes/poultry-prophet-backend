package com.poultryprophet.sexcomposition;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "batch_sex_composition")
public class BatchSexComposition {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "farm_id", nullable = false) private Long farmId;
    @Column(name = "batch_id", nullable = false) private Long batchId;
    @Column(name = "observed_on", nullable = false) private LocalDate observedOn;
    @Column(name = "population_as_of_observation", nullable = false) private int populationAsOfObservation;
    @Column(name = "male_count", nullable = false) private int maleCount;
    @Column(name = "female_count", nullable = false) private int femaleCount;
    @Column(name = "unclassified_count", nullable = false) private int unclassifiedCount;
    @Column(name = "recorded_by", nullable = false) private Long recordedBy;
    @Column(name = "revision_reason", length = 500) private String revisionReason;
    @Column(columnDefinition = "text") private String notes;
    @Column(name = "operation_id", nullable = false, unique = true) private UUID operationId;
    @Column(name = "supersedes_record_id") private Long supersedesRecordId;
    /** Highest population-event id already represented by this manual baseline. */
    @Column(name = "baseline_event_id") private Long baselineEventId;
    @Column(nullable = false, length = 32) private String status = "CURRENT";
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt = Instant.now();

    public Long getId() { return id; } public void setId(Long v) { id = v; }
    public Long getFarmId() { return farmId; } public void setFarmId(Long v) { farmId = v; }
    public Long getBatchId() { return batchId; } public void setBatchId(Long v) { batchId = v; }
    public LocalDate getObservedOn() { return observedOn; } public void setObservedOn(LocalDate v) { observedOn = v; }
    public int getPopulationAsOfObservation() { return populationAsOfObservation; } public void setPopulationAsOfObservation(int v) { populationAsOfObservation = v; }
    public int getMaleCount() { return maleCount; } public void setMaleCount(int v) { maleCount = v; }
    public int getFemaleCount() { return femaleCount; } public void setFemaleCount(int v) { femaleCount = v; }
    public int getUnclassifiedCount() { return unclassifiedCount; } public void setUnclassifiedCount(int v) { unclassifiedCount = v; }
    public Long getRecordedBy() { return recordedBy; } public void setRecordedBy(Long v) { recordedBy = v; }
    public String getRevisionReason() { return revisionReason; } public void setRevisionReason(String v) { revisionReason = v; }
    public String getNotes() { return notes; } public void setNotes(String v) { notes = v; }
    public UUID getOperationId() { return operationId; } public void setOperationId(UUID v) { operationId = v; }
    public Long getSupersedesRecordId() { return supersedesRecordId; } public void setSupersedesRecordId(Long v) { supersedesRecordId = v; }
    public Long getBaselineEventId() { return baselineEventId; } public void setBaselineEventId(Long v) { baselineEventId = v; }
    public String getStatus() { return status; } public void setStatus(String v) { status = v; }
    public Instant getCreatedAt() { return createdAt; } public void setCreatedAt(Instant v) { createdAt = v; }
}
