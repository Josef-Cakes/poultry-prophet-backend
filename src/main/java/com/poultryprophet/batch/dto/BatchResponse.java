package com.poultryprophet.batch.dto;

import com.poultryprophet.batch.Batch;
import com.poultryprophet.batch.BatchStatus;
import com.poultryprophet.batch.LifecycleStage;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record BatchResponse(
        Long id,
        Long farmId,
        String name,
        int initialPopulation,
        int currentPopulation,
        String populationStatus,
        String populationWarning,
        LocalDate startDate,
        String bloodline,
        String source,
        Instant hatchDateConfirmedAt,
        Long hatchDateConfirmedByUserId,
        Long stageId,
        String stageName,
        // True when the stage shown is derived from the batch's age (not a manual override).
        boolean stageAuto,
        BatchStatus status,
        List<Long> handlerUserIds,
        Instant createdAt,
        Instant archivedAt,
        Long archivedByUserId,
        String archiveReason,
        BatchStatus preArchiveStatus
) {
    /**
     * @param effectiveStage the stage to display — either the age-derived stage or the manual
     *                       override, as resolved by {@code BatchService#resolveStage}.
     * @param stageAuto      whether {@code effectiveStage} was derived from age.
     */
    public static BatchResponse from(Batch batch, List<Long> handlerUserIds,
                                     LifecycleStage effectiveStage, boolean stageAuto) {
        return from(batch, handlerUserIds, effectiveStage, stageAuto,
                batch.getCurrentPopulation(), "VALID", null);
    }

    public static BatchResponse from(Batch batch, List<Long> handlerUserIds,
                                     LifecycleStage effectiveStage, boolean stageAuto,
                                     int displayPopulation, String populationStatus,
                                     String populationWarning) {
        return new BatchResponse(
                batch.getId(),
                batch.getFarmId(),
                batch.getName(),
                batch.getInitialPopulation(),
                displayPopulation,
                populationStatus,
                populationWarning,
                batch.getStartDate(),
                batch.getBloodline(),
                batch.getSource(),
                batch.getHatchDateConfirmedAt(),
                batch.getHatchDateConfirmedByUserId(),
                effectiveStage.getId(),
                effectiveStage.getName(),
                stageAuto,
                batch.getStatus(),
                handlerUserIds,
                batch.getCreatedAt(),
                batch.getArchivedAt(),
                batch.getArchivedByUserId(),
                batch.getArchiveReason(),
                batch.getPreArchiveStatus());
    }
}
