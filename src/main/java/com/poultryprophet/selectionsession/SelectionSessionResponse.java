package com.poultryprophet.selectionsession;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;

public record SelectionSessionResponse(
        Long id,
        Long farmId,
        Long batchId,
        LocalDate selectionDate,
        Long reviewerId,
        int evaluatedCount,
        int acceptedCount,
        int continueObservationCount,
        int notAcceptedCount,
        int otherCount,
        Double selectionRatePercent,
        int selectionRateNumerator,
        int selectionRateDenominator,
        SelectionSessionStatus status,
        Set<String> criterionCodes,
        String criteriaNotes,
        String sessionNotes,
        String operationId,
        Long supersedesSessionId,
        Instant createdAt,
        Instant updatedAt,
        Instant finalizedAt,
        Long selectionReviewId,
        boolean sourceDataAcknowledged,
        long version
) {
    /** Compatibility constructor for records serialized before a source report was required. */
    public SelectionSessionResponse(Long id, Long farmId, Long batchId, LocalDate selectionDate, Long reviewerId,
                                    int evaluatedCount, int acceptedCount, int continueObservationCount,
                                    int notAcceptedCount, int otherCount, Double selectionRatePercent,
                                    int selectionRateNumerator, int selectionRateDenominator,
                                    SelectionSessionStatus status, Set<String> criterionCodes, String criteriaNotes,
                                    String sessionNotes, String operationId, Long supersedesSessionId,
                                    Instant createdAt, Instant updatedAt, Instant finalizedAt) {
        this(id, farmId, batchId, selectionDate, reviewerId, evaluatedCount, acceptedCount,
                continueObservationCount, notAcceptedCount, otherCount, selectionRatePercent,
                selectionRateNumerator, selectionRateDenominator, status, criterionCodes, criteriaNotes,
                sessionNotes, operationId, supersedesSessionId, createdAt, updatedAt, finalizedAt, null, false, 0);
    }
}
