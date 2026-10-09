package com.poultryprophet.batch.dto;

import com.poultryprophet.batch.BatchStatus;
import java.util.Map;
import java.util.List;

public record BatchRetirementImpactResponse(
        Long batchId,
        String batchName,
        BatchStatus status,
        boolean canArchive,
        boolean canDelete,
        long openTaskCount,
        long activeAlertCount,
        Map<String, Long> recordCounts,
        List<String> deleteBlockers
) {}
