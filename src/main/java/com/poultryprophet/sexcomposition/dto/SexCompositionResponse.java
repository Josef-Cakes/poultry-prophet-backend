package com.poultryprophet.sexcomposition.dto;

import com.poultryprophet.sexcomposition.BatchSexComposition;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record SexCompositionResponse(
        Long id, Long farmId, Long batchId, LocalDate observedOn, int populationAsOfObservation,
        int maleCount, int femaleCount, int unclassifiedCount, Long recordedBy,
        String revisionReason, String notes, UUID operationId, Long supersedesRecordId,
        String status, Instant createdAt, Long baselineEventId,
        LocalDate projectedAsOf, String projectionStatus, String projectionMessage
) {
    public static SexCompositionResponse from(BatchSexComposition row) {
        return new SexCompositionResponse(row.getId(), row.getFarmId(), row.getBatchId(), row.getObservedOn(),
                row.getPopulationAsOfObservation(), row.getMaleCount(), row.getFemaleCount(), row.getUnclassifiedCount(),
                row.getRecordedBy(), row.getRevisionReason(), row.getNotes(), row.getOperationId(),
                row.getSupersedesRecordId(), row.getStatus(), row.getCreatedAt(), row.getBaselineEventId(),
                null, null, null);
    }

    public static SexCompositionResponse fromProjected(BatchSexComposition row,
                                                        com.poultryprophet.sexcomposition.SexCompositionProjection projection) {
        return new SexCompositionResponse(row.getId(), row.getFarmId(), row.getBatchId(), row.getObservedOn(),
                projection.total(), projection.maleCount(), projection.femaleCount(), projection.unclassifiedCount(),
                row.getRecordedBy(), row.getRevisionReason(), row.getNotes(), row.getOperationId(),
                row.getSupersedesRecordId(), row.getStatus(), row.getCreatedAt(), row.getBaselineEventId(),
                projection.projectedAsOf(), projection.status(), projection.message());
    }
}
