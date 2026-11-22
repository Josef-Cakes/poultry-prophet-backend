package com.poultryprophet.selectionsession;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

public record CreateSelectionSessionRequest(
        LocalDate selectionDate,
        @Min(value = 1, message = "Enter at least one evaluated bird") int evaluatedCount,
        @Min(value = 0, message = "Accepted count cannot be negative") int acceptedCount,
        @Min(value = 0, message = "Continue-observation count cannot be negative") int continueObservationCount,
        @Min(value = 0, message = "Not-accepted count cannot be negative") int notAcceptedCount,
        @Min(value = 0, message = "Other count cannot be negative") int otherCount,
        @Size(max = 8, message = "Choose no more than 8 selection criteria") Set<String> criterionCodes,
        @Size(max = 1000, message = "Criteria notes must be 1000 characters or fewer") String criteriaNotes,
        @Size(max = 2000, message = "Session notes must be 2000 characters or fewer") String sessionNotes,
        UUID operationId,
        Long selectionReviewId,
        boolean sourceDataAcknowledged,
        @Min(0) Long expectedVersion
) {
    public CreateSelectionSessionRequest(LocalDate selectionDate, int evaluatedCount, int acceptedCount,
            int continueObservationCount, int notAcceptedCount, int otherCount, Set<String> criterionCodes,
            String criteriaNotes, String sessionNotes, UUID operationId, Long selectionReviewId, boolean sourceDataAcknowledged) {
        this(selectionDate, evaluatedCount, acceptedCount, continueObservationCount, notAcceptedCount, otherCount,
                criterionCodes, criteriaNotes, sessionNotes, operationId, selectionReviewId, sourceDataAcknowledged, null);
    }
    /** Retained for old queued records and focused tests created before report linking. */
    public CreateSelectionSessionRequest(LocalDate selectionDate, int evaluatedCount, int acceptedCount,
                                         int continueObservationCount, int notAcceptedCount, int otherCount,
                                         Set<String> criterionCodes, String criteriaNotes, String sessionNotes,
                                         UUID operationId) {
        this(selectionDate, evaluatedCount, acceptedCount, continueObservationCount, notAcceptedCount, otherCount,
                criterionCodes, criteriaNotes, sessionNotes, operationId, null, false, null);
    }
}
