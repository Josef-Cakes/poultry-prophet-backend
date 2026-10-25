package com.poultryprophet.incubation.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record CreateBatchFromIncubationRequest(@NotNull LocalDate farmEntryDate) {
}
