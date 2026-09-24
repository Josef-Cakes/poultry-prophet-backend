package com.poultryprophet.input.dto;

import com.poultryprophet.input.FarmInputLog;
import com.poultryprophet.input.InputProductType;

import java.time.Instant;

public record FarmInputLogResponse(
        Long id,
        Long farmId,
        Long batchId,
        Long incubationCycleId,
        Instant recordedAt,
        InputProductType productType,
        String brandName,
        String productName,
        Double quantity,
        String unit,
        String route,
        String purpose,
        String notes,
        Long recordedBy,
        Instant createdAt
) {
    public static FarmInputLogResponse from(FarmInputLog log) {
        return new FarmInputLogResponse(log.getId(), log.getFarmId(), log.getBatchId(),
                log.getIncubationCycleId(), log.getRecordedAt(), log.getProductType(),
                log.getBrandName(), log.getProductName(), log.getQuantity(), log.getUnit(),
                log.getRoute(), log.getPurpose(), log.getNotes(), log.getRecordedBy(), log.getCreatedAt());
    }
}
