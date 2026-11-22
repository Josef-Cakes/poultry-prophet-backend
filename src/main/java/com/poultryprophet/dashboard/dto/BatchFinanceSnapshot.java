package com.poultryprophet.dashboard.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Compact, manager-only cost context for a dashboard batch card.
 *
 * <p>A numeric total is intentionally omitted when a batch has recorded costs in more than one
 * currency. The dashboard must never add unlike currencies; the full batch report remains the
 * place to inspect each currency total.</p>
 */
public record BatchFinanceSnapshot(
        BigDecimal recordedBatchCost,
        boolean hasCostRecords,
        boolean productCostComplete,
        long unvaluedUsageCount,
        String currency,
        LocalDate asOfDate
) {
}
