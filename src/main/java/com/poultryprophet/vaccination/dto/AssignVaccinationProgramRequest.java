package com.poultryprophet.vaccination.dto;
import jakarta.validation.constraints.NotNull;
public record AssignVaccinationProgramRequest(@NotNull Long programId) {}
