package com.poultryprophet.task;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "handler_task_status_history")
public class TaskStatusHistory {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false) private Long taskId;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private TaskStatus status;
    @Column(nullable = false) private Long changedBy;
    @Column(columnDefinition = "text") private String note;
    @Column(nullable = false, updatable = false) private Instant changedAt = Instant.now();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getTaskId() { return taskId; }
    public void setTaskId(Long taskId) { this.taskId = taskId; }
    public TaskStatus getStatus() { return status; }
    public void setStatus(TaskStatus status) { this.status = status; }
    public Long getChangedBy() { return changedBy; }
    public void setChangedBy(Long changedBy) { this.changedBy = changedBy; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
    public Instant getChangedAt() { return changedAt; }
    public void setChangedAt(Instant changedAt) { this.changedAt = changedAt; }
}
