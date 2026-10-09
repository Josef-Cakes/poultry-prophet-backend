package com.poultryprophet.vaccination;

import com.poultryprophet.input.FarmInputLog;
import com.poultryprophet.input.InputProductType;
import com.poultryprophet.task.HandlerTaskRepository;
import com.poultryprophet.task.TaskStatus;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Locale;

/** Links normal vaccine product records to the matching scheduled vaccine step. */
@Service
public class VaccinationInputLinker {
    private final BatchVaccinationPlanItemRepository planRepository;
    private final HandlerTaskRepository taskRepository;

    public VaccinationInputLinker(BatchVaccinationPlanItemRepository planRepository,
                                  HandlerTaskRepository taskRepository) {
        this.planRepository = planRepository;
        this.taskRepository = taskRepository;
    }

    public void linkIfScheduled(FarmInputLog input) {
        if (input == null || input.getBatchId() == null
                || input.getProductType() != InputProductType.VACCINE) return;

        List<BatchVaccinationPlanItem> scheduled = planRepository
                .findByBatchIdAndFarmIdOrderByDueDateAsc(input.getBatchId(), input.getFarmId())
                .stream()
                .filter(item -> "SCHEDULED".equals(item.getStatus()))
                .toList();
        BatchVaccinationPlanItem match = findMatch(scheduled, input);
        if (match == null) return;

        Instant completedAt = input.getRecordedAt() == null ? Instant.now() : input.getRecordedAt();
        match.setStatus("COMPLETED");
        match.setCompletedInputLogId(input.getId());
        match.setCompletedBy(input.getRecordedBy());
        match.setCompletedAt(completedAt);
        match.setCompletionOperationId(input.getOperationId());
        planRepository.save(match);

        if (match.getTaskId() != null) {
            taskRepository.findByIdAndFarmId(match.getTaskId(), input.getFarmId()).ifPresent(task -> {
                if (task.getStatus() != TaskStatus.CANCELLED) {
                    task.setStatus(TaskStatus.COMPLETED);
                    task.setCompletedBy(input.getRecordedBy());
                    task.setCompletedAt(completedAt);
                    task.setUpdatedAt(Instant.now());
                    task.setCompletionNote("Completed from vaccine product record");
                    taskRepository.save(task);
                }
            });
        }
    }

    private BatchVaccinationPlanItem findMatch(List<BatchVaccinationPlanItem> candidates, FarmInputLog input) {
        if (input.getFarmProductId() != null) {
            for (BatchVaccinationPlanItem candidate : candidates) {
                if (input.getFarmProductId().equals(candidate.getFarmProductId())) return candidate;
            }
        }
        String brandName = normalize(input.getBrandName());
        String productName = normalize(input.getProductName());
        for (BatchVaccinationPlanItem candidate : candidates) {
            String vaccineName = normalize(candidate.getVaccineName());
            if ((!brandName.isBlank() && vaccineName.equals(brandName))
                    || (!productName.isBlank() && vaccineName.equals(productName))) return candidate;
        }
        return null;
    }

    private String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "");
    }
}
