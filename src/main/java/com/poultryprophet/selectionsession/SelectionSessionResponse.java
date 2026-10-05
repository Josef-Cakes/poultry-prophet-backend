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
        Instant finalizedAt
) {
}
