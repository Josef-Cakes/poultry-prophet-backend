package com.poultryprophet.incubation.dto;

import java.time.LocalDate;

/** A live reminder derived from the current cycle, rather than a stored notification. */
public record IncubationAttentionResponse(Long cycleId, String cycleName, LocalDate expectedHatchDate,
                                           LocalDate actualHatchDate, Integer hatchedCount,
                                           String kind, LocalDate asOfDate) {
}
