package com.poultryprophet.farm.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateFarmRequest(
        @NotBlank(message = "Farm name is required") @Size(max = 255) String name,
        @Size(max = 255) String location,
        @Size(max = 1000) String description
) {
}
