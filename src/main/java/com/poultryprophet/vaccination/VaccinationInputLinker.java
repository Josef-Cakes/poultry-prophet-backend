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
            List<BatchVaccinationPlanItem> matches = candidates.stream()
                    .filter(candidate -> input.getFarmProductId().equals(candidate.getFarmProductId())).toList();
            return matches.size() == 1 ? matches.get(0) : null;
        }
        // Legacy untracked records may match an unlinked schedule only when the
        // match is unambiguous. Never use a name to override a product identity.
        String brandName = normalize(input.getBrandName());
        String productName = normalize(input.getProductName());
        List<BatchVaccinationPlanItem> matches = candidates.stream()
                .filter(candidate -> candidate.getFarmProductId() == null)
                .filter(candidate -> {
                    String name = normalize(candidate.getVaccineName());
                    return (!brandName.isBlank() && name.equals(brandName)) || (!productName.isBlank() && name.equals(productName));
                }).toList();
        return matches.size() == 1 ? matches.get(0) : null;
    }

    private String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "");
    }
}
