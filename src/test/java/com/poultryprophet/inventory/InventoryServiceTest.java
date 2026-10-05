package com.poultryprophet.inventory;

import com.poultryprophet.batch.BatchService;
import com.poultryprophet.finance.FinanceService;
import com.poultryprophet.inventory.dto.InventoryUseResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InventoryServiceTest {
    @Mock private FarmProductRepository productRepository;
    @Mock private InventoryMovementRepository movementRepository;
    @Mock private BatchService batchService;
    @Mock private FinanceService financeService;

    @Test
    void deductsOneTrackedUseAndStoresTheNewBalance() {
        FarmProduct product = product("sachet", "5");
        when(productRepository.findByIdAndFarmIdForUpdate(10L, 3L)).thenReturn(Optional.of(product));
        when(movementRepository.existsByFarmIdAndFarmInputLogId(3L, 44L)).thenReturn(false);
        when(productRepository.save(any(FarmProduct.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(movementRepository.save(any(InventoryMovement.class))).thenAnswer(invocation -> {
            InventoryMovement value = invocation.getArgument(0);
            value.setId(99L);
            return value;
        });

        InventoryUseResult result = new InventoryService(productRepository, movementRepository, batchService, financeService)
                .applyUsage(3L, 7L, 10L, new BigDecimal("1"), Instant.now(), 8L, 44L);

        assertThat(result.status()).isEqualTo(InventoryStatus.DEDUCTED.name());
        assertThat(result.movementId()).isEqualTo(99L);
        assertThat(result.balanceAfter()).isEqualByComparingTo("4.000");
        assertThat(product.getStockOnHand()).isEqualByComparingTo("4.000");
        verify(movementRepository).save(any(InventoryMovement.class));
    }

    @Test
    void preservesUseAsPendingWhenStockIsInsufficient() {
        FarmProduct product = product("bottle", "0.5");
        when(productRepository.findByIdAndFarmIdForUpdate(10L, 3L)).thenReturn(Optional.of(product));
        when(movementRepository.existsByFarmIdAndFarmInputLogId(3L, 44L)).thenReturn(false);

        InventoryUseResult result = new InventoryService(productRepository, movementRepository, batchService, financeService)
                .applyUsage(3L, 7L, 10L, new BigDecimal("1"), Instant.now(), 8L, 44L);

        assertThat(result.status()).isEqualTo(InventoryStatus.PENDING_STOCK_REVIEW.name());
        assertThat(result.movementId()).isNull();
        assertThat(product.getStockOnHand()).isEqualByComparingTo("0.5");
        verify(movementRepository, never()).save(any(InventoryMovement.class));
    }

    private static FarmProduct product(String unit, String stock) {
        FarmProduct product = new FarmProduct();
        product.setId(10L);
        product.setFarmId(3L);
        product.setStockUnit(unit);
        product.setStockOnHand(new BigDecimal(stock));
        product.setActive(true);
        product.setAllowFractionalQuantity(true);
        return product;
    }
}
