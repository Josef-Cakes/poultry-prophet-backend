package com.poultryprophet.alert;

import com.poultryprophet.analytics.Indicator;
import com.poultryprophet.batch.Batch;
import com.poultryprophet.event.BatchEvent;
import com.poultryprophet.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.OneToOne;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "alert", indexes = {
        @Index(name = "idx_alert_batch_created", columnList = "batch_id, created_at"),
        @Index(name = "idx_alert_severity_ack", columnList = "severity, acknowledged_at"),
        @Index(name = "idx_alert_source_event", columnList = "source_event_id", unique = true)
})
public class Alert {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "batch_id", nullable = false)
    private Batch batch;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "indicator_id")
    private Indicator indicator;

    @Column(nullable = false)
    private String indicatorType;

    /** Canonical mortality source. A unique link makes direct alerts idempotent. */
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_event_id", unique = true)
    private BatchEvent sourceEvent;

    @Column(name = "batch_name")
    private String batchName;

    @Column(name = "handler_name")
    private String handlerName;

    @Column(name = "death_count")
    private Integer deathCount;

    @Column(name = "cause")
    private String cause;

    @Column(name = "occurrence_date")
    private LocalDate occurrenceDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Severity severity;

    @Column(nullable = false, columnDefinition = "text")
    private String message;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "acknowledged_by")
    private User acknowledgedBy;

    @Column(name = "acknowledged_at")
    private Instant acknowledgedAt;

    @Column(columnDefinition = "text")
    private String acknowledgmentNote;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

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

    public Indicator getIndicator() {
        return indicator;
    }

    public void setIndicator(Indicator indicator) {
        this.indicator = indicator;
    }

    public String getIndicatorType() {
        return indicatorType;
    }

    public void setIndicatorType(String indicatorType) {
        this.indicatorType = indicatorType;
    }

    public BatchEvent getSourceEvent() {
        return sourceEvent;
    }

    public void setSourceEvent(BatchEvent sourceEvent) {
        this.sourceEvent = sourceEvent;
    }

    public String getBatchName() {
        return batchName;
    }

    public void setBatchName(String batchName) {
        this.batchName = batchName;
    }

    public String getHandlerName() {
        return handlerName;
    }

    public void setHandlerName(String handlerName) {
        this.handlerName = handlerName;
    }

    public Integer getDeathCount() {
        return deathCount;
    }

    public void setDeathCount(Integer deathCount) {
        this.deathCount = deathCount;
    }

    public String getCause() {
        return cause;
    }

    public void setCause(String cause) {
        this.cause = cause;
    }

    public LocalDate getOccurrenceDate() {
        return occurrenceDate;
    }

    public void setOccurrenceDate(LocalDate occurrenceDate) {
        this.occurrenceDate = occurrenceDate;
    }

    public Severity getSeverity() {
        return severity;
    }

    public void setSeverity(Severity severity) {
        this.severity = severity;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public User getAcknowledgedBy() {
        return acknowledgedBy;
    }

    public void setAcknowledgedBy(User acknowledgedBy) {
        this.acknowledgedBy = acknowledgedBy;
    }

    public Instant getAcknowledgedAt() {
        return acknowledgedAt;
    }

    public void setAcknowledgedAt(Instant acknowledgedAt) {
        this.acknowledgedAt = acknowledgedAt;
    }

    public String getAcknowledgmentNote() {
        return acknowledgmentNote;
    }

    public void setAcknowledgmentNote(String acknowledgmentNote) {
        this.acknowledgmentNote = acknowledgmentNote;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
