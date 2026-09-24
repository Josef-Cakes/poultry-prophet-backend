package com.poultryprophet.task;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "handler_task")
public class HandlerTask {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false) private Long farmId;
    private Long batchId;
    private Long incubationCycleId;
    @Column(nullable = false) private String title;
    @Column(columnDefinition = "text") private String instructions;
    private Long assignedHandlerId;
    @Column(nullable = false) private Long assignedManagerId;
    private Instant dueAt;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private TaskPriority priority = TaskPriority.NORMAL;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private TaskStatus status = TaskStatus.TODO;
    @Column(columnDefinition = "text") private String completionNote;
    private Instant completedAt;
    @Column(nullable = false, updatable = false) private Instant createdAt = Instant.now();
    @Column(nullable = false) private Instant updatedAt = Instant.now();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getFarmId() { return farmId; }
    public void setFarmId(Long farmId) { this.farmId = farmId; }
    public Long getBatchId() { return batchId; }
    public void setBatchId(Long batchId) { this.batchId = batchId; }
    public Long getIncubationCycleId() { return incubationCycleId; }
    public void setIncubationCycleId(Long incubationCycleId) { this.incubationCycleId = incubationCycleId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getInstructions() { return instructions; }
    public void setInstructions(String instructions) { this.instructions = instructions; }
    public Long getAssignedHandlerId() { return assignedHandlerId; }
    public void setAssignedHandlerId(Long assignedHandlerId) { this.assignedHandlerId = assignedHandlerId; }
    public Long getAssignedManagerId() { return assignedManagerId; }
    public void setAssignedManagerId(Long assignedManagerId) { this.assignedManagerId = assignedManagerId; }
    public Instant getDueAt() { return dueAt; }
    public void setDueAt(Instant dueAt) { this.dueAt = dueAt; }
    public TaskPriority getPriority() { return priority; }
    public void setPriority(TaskPriority priority) { this.priority = priority; }
    public TaskStatus getStatus() { return status; }
    public void setStatus(TaskStatus status) { this.status = status; }
    public String getCompletionNote() { return completionNote; }
    public void setCompletionNote(String completionNote) { this.completionNote = completionNote; }
    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
