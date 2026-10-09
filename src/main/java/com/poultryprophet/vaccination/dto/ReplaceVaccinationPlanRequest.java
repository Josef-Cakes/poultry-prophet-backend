package com.poultryprophet.vaccination.dto;

import jakarta.validation.constraints.NotNull;

/** Selects a new farm vaccination-program version for an existing batch plan. */
public record ReplaceVaccinationPlanRequest(@NotNull Long programId) {
}
