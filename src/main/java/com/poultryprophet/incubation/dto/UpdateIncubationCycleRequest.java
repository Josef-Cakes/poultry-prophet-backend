package com.poultryprophet.incubation.dto;

import java.time.LocalDate;

public record UpdateIncubationCycleRequest(
        String incubatorCode,
        String eggSource,
        String bloodline,
        LocalDate expectedHatchDate,
        String notes,
        String status
) {
}
