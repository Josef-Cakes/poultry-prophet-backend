package com.poultryprophet.inventory.dto;

import java.math.BigDecimal;

public record InventoryUseResult(Long movementId, String status, BigDecimal balanceAfter, String message) {}
