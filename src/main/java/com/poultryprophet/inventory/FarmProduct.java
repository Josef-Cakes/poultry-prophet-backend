package com.poultryprophet.inventory;

import com.poultryprophet.input.InputProductType;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "farm_product", indexes = {
        @Index(name = "idx_farm_product_farm_active", columnList = "farm_id,active"),
        @Index(name = "idx_farm_product_name", columnList = "farm_id,brand_name")
})
public class FarmProduct {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "farm_id", nullable = false) private Long farmId;
    @Enumerated(EnumType.STRING) @Column(name = "product_type", nullable = false, length = 32) private InputProductType productType;
    @Column(name = "brand_name", nullable = false) private String brandName;
    @Column(name = "product_name") private String productName;
    @Column(name = "package_description") private String packageDescription;
    @Column(name = "stock_unit", nullable = false, length = 32) private String stockUnit;
    @Column(name = "stock_on_hand", nullable = false, precision = 14, scale = 3) private BigDecimal stockOnHand = BigDecimal.ZERO;
    @Column(name = "average_unit_cost", precision = 16, scale = 6) private BigDecimal averageUnitCost;
    @Column(nullable = false, length = 3) private String currency = "PHP";
    @Enumerated(EnumType.STRING) @Column(name = "valuation_status", nullable = false, length = 16)
    private InventoryValuationStatus valuationStatus = InventoryValuationStatus.UNVALUED;
    @Column(name = "reorder_level", precision = 14, scale = 3) private BigDecimal reorderLevel;
    @Column(name = "allow_fractional_quantity", nullable = false) private boolean allowFractionalQuantity = true;
    @Column(nullable = false) private boolean active = true;
    @Version @Column(nullable = false) private long version;
    @Column(name = "created_by", nullable = false) private Long createdBy;
    @Column(nullable = false, updatable = false) private Instant createdAt = Instant.now();
    @Column(nullable = false) private Instant updatedAt = Instant.now();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getFarmId() { return farmId; }
    public void setFarmId(Long farmId) { this.farmId = farmId; }
    public InputProductType getProductType() { return productType; }
    public void setProductType(InputProductType productType) { this.productType = productType; }
    public String getBrandName() { return brandName; }
    public void setBrandName(String brandName) { this.brandName = brandName; }
    public String getProductName() { return productName; }
    public void setProductName(String productName) { this.productName = productName; }
    public String getPackageDescription() { return packageDescription; }
    public void setPackageDescription(String packageDescription) { this.packageDescription = packageDescription; }
    public String getStockUnit() { return stockUnit; }
    public void setStockUnit(String stockUnit) { this.stockUnit = stockUnit; }
    public BigDecimal getStockOnHand() { return stockOnHand; }
    public void setStockOnHand(BigDecimal stockOnHand) { this.stockOnHand = stockOnHand; }
    public BigDecimal getAverageUnitCost() { return averageUnitCost; }
    public void setAverageUnitCost(BigDecimal averageUnitCost) { this.averageUnitCost = averageUnitCost; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public InventoryValuationStatus getValuationStatus() { return valuationStatus; }
    public void setValuationStatus(InventoryValuationStatus valuationStatus) { this.valuationStatus = valuationStatus; }
    public BigDecimal getReorderLevel() { return reorderLevel; }
    public void setReorderLevel(BigDecimal reorderLevel) { this.reorderLevel = reorderLevel; }
    public boolean isAllowFractionalQuantity() { return allowFractionalQuantity; }
    public void setAllowFractionalQuantity(boolean allowFractionalQuantity) { this.allowFractionalQuantity = allowFractionalQuantity; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public long getVersion() { return version; }
    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void touch() { this.updatedAt = Instant.now(); }
}
