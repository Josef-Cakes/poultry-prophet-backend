package com.poultryprophet.inventory.dto;

import com.poultryprophet.input.InputProductType;
import com.poultryprophet.inventory.FarmProduct;
import java.math.BigDecimal;

public record FarmProductResponse(
        Long id, Long farmId, InputProductType productType, String brandName, String productName,
        String packageDescription, String stockUnit, BigDecimal stockOnHand, BigDecimal reorderLevel,
        BigDecimal averageUnitCost, BigDecimal inventoryValue, String currency, String valuationStatus,
        boolean lowStock, boolean allowFractionalQuantity, boolean active
) {
    public static FarmProductResponse from(FarmProduct product) {
        boolean low = product.getReorderLevel() != null
                && product.getStockOnHand().compareTo(product.getReorderLevel()) <= 0;
        BigDecimal inventoryValue = product.getAverageUnitCost() == null ? null
                : product.getStockOnHand().multiply(product.getAverageUnitCost()).setScale(2, java.math.RoundingMode.HALF_UP);
        return new FarmProductResponse(product.getId(), product.getFarmId(), product.getProductType(),
                product.getBrandName(), product.getProductName(), product.getPackageDescription(),
                product.getStockUnit(), product.getStockOnHand(), product.getReorderLevel(), product.getAverageUnitCost(),
                inventoryValue, product.getCurrency(), product.getValuationStatus() == null ? null : product.getValuationStatus().name(), low,
                product.isAllowFractionalQuantity(), product.isActive());
    }
}
