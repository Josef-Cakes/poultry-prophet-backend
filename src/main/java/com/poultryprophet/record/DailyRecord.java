package com.poultryprophet.record;

import com.poultryprophet.batch.Batch;
import com.poultryprophet.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "daily_record",
        uniqueConstraints = @UniqueConstraint(columnNames = {"batch_id", "record_date"}))
public class DailyRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "batch_id", nullable = false)
    private Batch batch;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "handler_id", nullable = false)
    private User handler;

    @Column(name = "record_date", nullable = false)
    private LocalDate recordDate;

    @Column(nullable = false)
    private double temperatureC;

    @Column(nullable = false)
    private int mortalityCount;

    /** Optional: farms may record feed qualitatively when no scale is available. */
    @Column
    private Double feedIntakeG;

    /** Optional: water use is often estimated rather than metered. */
    @Column
    private Double waterIntakeMl;

    @Enumerated(EnumType.STRING)
    @Column
    private ObservationQuality temperatureQuality = ObservationQuality.UNKNOWN;

    @Enumerated(EnumType.STRING)
    @Column
    private ObservationQuality feedQuality = ObservationQuality.UNKNOWN;

    @Enumerated(EnumType.STRING)
    @Column
    private ObservationQuality waterQuality = ObservationQuality.UNKNOWN;

    @Column(columnDefinition = "text")
    private String behaviorNotes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SyncStatus syncStatus = SyncStatus.SYNCED;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    /** Client-supplied last-modified time, used for offline conflict resolution (SDD 1.2). */
    @Column(nullable = false)
    private Instant updatedAt = Instant.now();

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Batch getBatch() {
        return batch;
    }

    public void setBatch(Batch batch) {
        this.batch = batch;
    }

    public User getHandler() {
        return handler;
    }

    public void setHandler(User handler) {
        this.handler = handler;
    }

    public LocalDate getRecordDate() {
        return recordDate;
    }

    public void setRecordDate(LocalDate recordDate) {
        this.recordDate = recordDate;
    }

    public double getTemperatureC() {
        return temperatureC;
    }

    public void setTemperatureC(double temperatureC) {
        this.temperatureC = temperatureC;
    }

    public int getMortalityCount() {
        return mortalityCount;
    }

    public void setMortalityCount(int mortalityCount) {
        this.mortalityCount = mortalityCount;
    }

    public Double getFeedIntakeG() {
        return feedIntakeG;
    }

    public void setFeedIntakeG(Double feedIntakeG) {
        this.feedIntakeG = feedIntakeG;
    }

    public Double getWaterIntakeMl() {
        return waterIntakeMl;
    }

    public void setWaterIntakeMl(Double waterIntakeMl) {
        this.waterIntakeMl = waterIntakeMl;
    }

    public ObservationQuality getTemperatureQuality() {
        return temperatureQuality == null ? ObservationQuality.UNKNOWN : temperatureQuality;
    }

    public void setTemperatureQuality(ObservationQuality temperatureQuality) {
        this.temperatureQuality = temperatureQuality == null ? ObservationQuality.UNKNOWN : temperatureQuality;
    }

    public ObservationQuality getFeedQuality() {
        return feedQuality == null ? ObservationQuality.UNKNOWN : feedQuality;
    }

    public void setFeedQuality(ObservationQuality feedQuality) {
        this.feedQuality = feedQuality == null ? ObservationQuality.UNKNOWN : feedQuality;
    }

    public ObservationQuality getWaterQuality() {
        return waterQuality == null ? ObservationQuality.UNKNOWN : waterQuality;
    }

    public void setWaterQuality(ObservationQuality waterQuality) {
        this.waterQuality = waterQuality == null ? ObservationQuality.UNKNOWN : waterQuality;
    }

    public String getBehaviorNotes() {
        return behaviorNotes;
    }

    public void setBehaviorNotes(String behaviorNotes) {
        this.behaviorNotes = behaviorNotes;
    }

    public SyncStatus getSyncStatus() {
        return syncStatus;
    }

    public void setSyncStatus(SyncStatus syncStatus) {
        this.syncStatus = syncStatus;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
