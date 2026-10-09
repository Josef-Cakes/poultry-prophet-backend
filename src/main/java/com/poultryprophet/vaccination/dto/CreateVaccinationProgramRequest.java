package com.poultryprophet.vaccination.dto;
import jakarta.validation.Valid; import jakarta.validation.constraints.NotBlank; import jakarta.validation.constraints.NotEmpty; import java.util.List; import java.util.UUID;
public record CreateVaccinationProgramRequest(@NotBlank String name, String description, Boolean defaultForNewBatches, UUID seriesId, Long supersedesProgramId, @NotEmpty List<@Valid VaccinationProgramItemRequest> items) {}
