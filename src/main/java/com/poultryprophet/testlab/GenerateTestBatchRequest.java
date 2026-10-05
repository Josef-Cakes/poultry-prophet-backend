package com.poultryprophet.testlab;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record GenerateTestBatchRequest(
        @NotNull Long sourceBatchId,
        TestLabProfile profile,
        Long seed,
        @NotBlank String confirmation
) {
}
