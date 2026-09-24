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
        LocalDate startDate,
        String bloodline,
        String source,
        Long stageId,
        String stageName,
        // True when the stage shown is derived from the batch's age (not a manual override).
        boolean stageAuto,
        BatchStatus status,
        List<Long> handlerUserIds,
        Instant createdAt
) {
    /**
     * @param effectiveStage the stage to display — either the age-derived stage or the manual
     *                       override, as resolved by {@code BatchService#resolveStage}.
     * @param stageAuto      whether {@code effectiveStage} was derived from age.
     */
    public static BatchResponse from(Batch batch, List<Long> handlerUserIds,
                                     LifecycleStage effectiveStage, boolean stageAuto) {
        return new BatchResponse(
                batch.getId(),
                batch.getFarmId(),
                batch.getName(),
                batch.getInitialPopulation(),
                batch.getCurrentPopulation(),
                batch.getStartDate(),
                batch.getBloodline(),
                batch.getSource(),
                effectiveStage.getId(),
                effectiveStage.getName(),
                stageAuto,
                batch.getStatus(),
                handlerUserIds,
                batch.getCreatedAt());
    }
}
