package com.poultryprophet.vaccination.dto;
import jakarta.validation.constraints.Min; import jakarta.validation.constraints.NotBlank; import jakarta.validation.constraints.NotNull;
public record VaccinationProgramItemRequest(@NotBlank String vaccineName, Long farmProductId, @NotNull @Min(0) Integer ageOffsetValue, @NotBlank String ageOffsetUnit, @Min(0) Integer reminderLeadDays, String route, String doseGuidance, String instructions) {}
