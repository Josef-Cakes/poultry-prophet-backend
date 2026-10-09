package com.poultryprophet.batch.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public record ConfirmHatchDateRequest(@NotNull LocalDate hatchDate) {}
