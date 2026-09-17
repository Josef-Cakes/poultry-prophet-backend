package com.poultryprophet.event;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "batch_event",
        indexes = @jakarta.persistence.Index(name = "idx_batch_event_operation_id", columnList = "operation_id", unique = true))
public class BatchEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "batch_id", nullable = false)
    private Long batchId;

    @Column(name = "handler_id", nullable = false)
    private Long handlerId;

    /** Client operation identity. Nullable only for legacy rows imported before idempotency. */
    @Column(name = "operation_id", unique = true)
    private UUID operationId;

    @Column(name = "event_date", nullable = false)
    private LocalDate eventDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false)
    private EventType eventType;

    /** Contextual severity / category label: e.g. "MINOR", "VACCINATION", "HIGH". */
    @Column(name = "severity_label")
    private String severityLabel;

    /** Number of birds directly involved in the event. 0 = general observation. */
    @Column(name = "affected_count", nullable = false)
    private int affectedCount;

    /** Signed population effect applied by the ledger. Zero for informational events. */
    @Column(name = "population_delta")
    private Integer populationDelta;

    /** Population after this event committed; useful for idempotent retry responses/audit. */
    @Column(name = "population_after")
    private Integer populationAfter;

    /** Short title — primary cause, medicine name, or behaviour category. */
    @Column(nullable = false)
    private String title;

    /** Free-text additional notes from the handler. */
    @Column(columnDefinition = "text")
    private String details;

    /** Comma-separated predefined tags (symptoms, behaviours, route, etc.). */
    @Column(columnDefinition = "text")
    private String tags;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getBatchId() { return batchId; }
    public void setBatchId(Long batchId) { this.batchId = batchId; }

    public Long getHandlerId() { return handlerId; }
    public void setHandlerId(Long handlerId) { this.handlerId = handlerId; }

    public UUID getOperationId() { return operationId; }
    public void setOperationId(UUID operationId) { this.operationId = operationId; }

    public LocalDate getEventDate() { return eventDate; }
    public void setEventDate(LocalDate eventDate) { this.eventDate = eventDate; }

    public EventType getEventType() { return eventType; }
    public void setEventType(EventType eventType) { this.eventType = eventType; }

    public String getSeverityLabel() { return severityLabel; }
    public void setSeverityLabel(String severityLabel) { this.severityLabel = severityLabel; }

    public int getAffectedCount() { return affectedCount; }
    public void setAffectedCount(int affectedCount) { this.affectedCount = affectedCount; }

    public Integer getPopulationDelta() { return populationDelta; }
    public void setPopulationDelta(Integer populationDelta) { this.populationDelta = populationDelta; }

    public Integer getPopulationAfter() { return populationAfter; }
    public void setPopulationAfter(Integer populationAfter) { this.populationAfter = populationAfter; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDetails() { return details; }
    public void setDetails(String details) { this.details = details; }

    public String getTags() { return tags; }
    public void setTags(String tags) { this.tags = tags; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
