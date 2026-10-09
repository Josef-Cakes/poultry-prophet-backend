package com.poultryprophet.inventory.dto;

import java.math.BigDecimal;

public record InventoryUseResult(Long movementId, String status, BigDecimal balanceBefore, BigDecimal balanceAfter,
                                 BigDecimal quantityUsed, String unit, BigDecimal unitCost, BigDecimal batchCost,
                                 String costStatus, String message) {}
