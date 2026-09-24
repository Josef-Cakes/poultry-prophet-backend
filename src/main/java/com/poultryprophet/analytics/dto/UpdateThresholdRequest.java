package com.poultryprophet.analytics.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

public record UpdateThresholdRequest(
        @NotNull @DecimalMin(value = "0.0", message = "must be greater than or equal to 0") Double minValue,
        @NotNull @DecimalMin(value = "0.0", message = "must be greater than or equal to 0") Double maxValue
) {

    @AssertTrue(message = "threshold values must be finite")
    public boolean hasFiniteValues() {
        return minValue != null
                && maxValue != null
                && Double.isFinite(minValue)
                && Double.isFinite(maxValue);
    }

    @AssertTrue(message = "minValue must not exceed maxValue")
    public boolean hasValidOrder() {
        return minValue == null || maxValue == null || minValue <= maxValue;
    }
}
