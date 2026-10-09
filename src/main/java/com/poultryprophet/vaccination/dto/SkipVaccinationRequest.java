package com.poultryprophet.vaccination.dto;
import jakarta.validation.constraints.NotBlank;
public record SkipVaccinationRequest(@NotBlank String reason) {}
