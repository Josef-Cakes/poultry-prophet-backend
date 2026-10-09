package com.poultryprophet.vaccination.dto;
import com.poultryprophet.vaccination.BatchVaccinationPlanItem;
import com.poultryprophet.batch.BatchAgePolicy;
import java.time.*; import java.util.UUID;
public record VaccinationPlanItemResponse(Long id, Long batchId, Long programId, String vaccineName, Long farmProductId, LocalDate hatchDate, int ageDay, String stageName, LocalDate dueDate, LocalDate remindOn, String status, String displayState, Long taskId, Long completedInputLogId, Long completedBy, Instant completedAt, UUID completionOperationId, String skippedReason) {
 public static VaccinationPlanItemResponse from(BatchVaccinationPlanItem p, LocalDate today) { String state=p.getStatus(); if (!"COMPLETED".equals(state)&&!"SKIPPED".equals(state)&&!"CANCELLED".equals(state)) state=p.getDueDate().isBefore(today)?"OVERDUE":p.getDueDate().equals(today)?"DUE_TODAY":"UPCOMING"; return new VaccinationPlanItemResponse(p.getId(),p.getBatchId(),p.getProgramId(),p.getVaccineName(),p.getFarmProductId(),p.getHatchDateSnapshot(),p.getNormalizedOffsetDays(),BatchAgePolicy.stageName(p.getNormalizedOffsetDays()),p.getDueDate(),p.getRemindOn(),p.getStatus(),state,p.getTaskId(),p.getCompletedInputLogId(),p.getCompletedBy(),p.getCompletedAt(),p.getCompletionOperationId(),p.getSkippedReason()); }
}
