package com.poultryprophet.inventory.dto;

import com.poultryprophet.input.InputProductType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record CreateFarmProductRequest(
        @NotNull InputProductType productType,
        @NotBlank String brandName,
        String productName,
        String packageDescription,
        @NotBlank String stockUnit,
        @DecimalMin(value = "0.0") BigDecimal openingQuantity,
        @DecimalMin(value = "0.0") BigDecimal openingUnitCost,
        @DecimalMin(value = "0.0") BigDecimal reorderLevel,
        Boolean allowFractionalQuantity
) {}
