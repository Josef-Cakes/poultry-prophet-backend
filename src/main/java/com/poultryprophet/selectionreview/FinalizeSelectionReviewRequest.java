package com.poultryprophet.selectionreview;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record FinalizeSelectionReviewRequest(
        @NotNull ManagerReviewStatus reviewStatus,
        String managerNotes,
        LocalDate nextReviewDate
) {}
