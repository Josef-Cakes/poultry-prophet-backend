package com.poultryprophet.testlab;

import java.time.LocalDate;

public record GenerateTestBatchResponse(
        String classification,
        Long sourceBatchId,
        Long batchId,
        String batchName,
        TestLabProfile profile,
        long seed,
        LocalDate startDate,
        LocalDate endDate,
        int initialPopulation,
        int finalPopulation,
        int eventCount,
        int observationCount,
        int inputCount,
        int financeCount,
        int completedTaskCount,
        int reportCount
) {
}
