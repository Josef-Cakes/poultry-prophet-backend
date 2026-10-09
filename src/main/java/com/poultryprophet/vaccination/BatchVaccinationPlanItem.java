package com.poultryprophet.vaccination;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "batch_vaccination_plan_item")
public class BatchVaccinationPlanItem {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name="farm_id",nullable=false) private Long farmId; @Column(name="batch_id",nullable=false) private Long batchId;
    @Column(name="program_id",nullable=false) private Long programId; @Column(name="program_item_id",nullable=false) private Long programItemId;
    @Column(name="vaccine_name",nullable=false) private String vaccineName; @Column(name="farm_product_id") private Long farmProductId;
    @Column(name="hatch_date_snapshot",nullable=false) private LocalDate hatchDateSnapshot; @Column(name="normalized_offset_days",nullable=false) private int normalizedOffsetDays;
    @Column(name="due_date",nullable=false) private LocalDate dueDate; @Column(name="remind_on",nullable=false) private LocalDate remindOn;
    @Column(nullable=false) private String status="SCHEDULED"; @Column(name="task_id") private Long taskId; @Column(name="completed_input_log_id") private Long completedInputLogId;
    @Column(name="completed_by") private Long completedBy; @Column(name="completed_at") private Instant completedAt; @Column(name="completion_operation_id",unique=true) private UUID completionOperationId;
    @Column(name="skipped_reason",length=500) private String skippedReason; @Column(name="created_at",nullable=false,updatable=false) private Instant createdAt=Instant.now();
    public Long getId(){return id;} public void setId(Long v){id=v;} public Long getFarmId(){return farmId;} public void setFarmId(Long v){farmId=v;} public Long getBatchId(){return batchId;} public void setBatchId(Long v){batchId=v;} public Long getProgramId(){return programId;} public void setProgramId(Long v){programId=v;} public Long getProgramItemId(){return programItemId;} public void setProgramItemId(Long v){programItemId=v;}
    public String getVaccineName(){return vaccineName;} public void setVaccineName(String v){vaccineName=v;} public Long getFarmProductId(){return farmProductId;} public void setFarmProductId(Long v){farmProductId=v;} public LocalDate getHatchDateSnapshot(){return hatchDateSnapshot;} public void setHatchDateSnapshot(LocalDate v){hatchDateSnapshot=v;} public int getNormalizedOffsetDays(){return normalizedOffsetDays;} public void setNormalizedOffsetDays(int v){normalizedOffsetDays=v;} public LocalDate getDueDate(){return dueDate;} public void setDueDate(LocalDate v){dueDate=v;} public LocalDate getRemindOn(){return remindOn;} public void setRemindOn(LocalDate v){remindOn=v;}
    public String getStatus(){return status;} public void setStatus(String v){status=v;} public Long getTaskId(){return taskId;} public void setTaskId(Long v){taskId=v;} public Long getCompletedInputLogId(){return completedInputLogId;} public void setCompletedInputLogId(Long v){completedInputLogId=v;} public Long getCompletedBy(){return completedBy;} public void setCompletedBy(Long v){completedBy=v;} public Instant getCompletedAt(){return completedAt;} public void setCompletedAt(Instant v){completedAt=v;} public UUID getCompletionOperationId(){return completionOperationId;} public void setCompletionOperationId(UUID v){completionOperationId=v;} public String getSkippedReason(){return skippedReason;} public void setSkippedReason(String v){skippedReason=v;} public Instant getCreatedAt(){return createdAt;}
}
