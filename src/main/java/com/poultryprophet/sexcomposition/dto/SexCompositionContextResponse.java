package com.poultryprophet.sexcomposition.dto;

import java.time.LocalDate;

/** Population and revision context used to validate a sex-composition form. */
public record SexCompositionContextResponse(
        LocalDate observedOn,
        Integer expectedPopulation,
        String populationStatus,
        String populationMessage,
        Long currentRecordId
) {}
