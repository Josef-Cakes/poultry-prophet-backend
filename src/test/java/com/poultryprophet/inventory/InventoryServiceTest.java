package com.poultryprophet.inventory;

import com.poultryprophet.batch.BatchService;
import com.poultryprophet.common.BadRequestException;
import com.poultryprophet.finance.FinanceService;
import com.poultryprophet.finance.dto.FinancialTransactionResponse;
import com.poultryprophet.inventory.dto.InventoryUseResult;
import com.poultryprophet.inventory.dto.InventoryMovementResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
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
        product.setAverageUnitCost(new BigDecimal("12.50"));
        product.setValuationStatus(InventoryValuationStatus.VALUED);
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
        assertThat(result.batchCost()).isEqualByComparingTo("12.50");
        assertThat(result.unitCost()).isEqualByComparingTo("12.50");
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

    @Test
    void updatesMovingAverageWhenStockArrivesAtAnewPrice() {
        FarmProduct product = product("bag", "5");
        product.setAverageUnitCost(new BigDecimal("100"));
        product.setValuationStatus(InventoryValuationStatus.VALUED);
        when(productRepository.findByIdAndFarmIdForUpdate(10L, 3L)).thenReturn(Optional.of(product));
        when(movementRepository.findByOperationId(any())).thenReturn(Optional.empty());
        when(productRepository.save(any(FarmProduct.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(movementRepository.save(any(InventoryMovement.class))).thenAnswer(invocation -> {
            InventoryMovement value = invocation.getArgument(0);
            value.setId(100L);
            return value;
        });
        when(financeService.createInventoryPurchase(anyLong(), anyLong(), any())).thenReturn(
                new FinancialTransactionResponse(88L, null, null, null, null, null, null, null, null,
                        null, null, null, null, null, null, null, null));

        InventoryMovementResponse result = new InventoryService(productRepository, movementRepository, batchService, financeService)
                .stockIn(3L, 7L, 10L, new com.poultryprophet.inventory.dto.StockInRequest(
                        new BigDecimal("5"), null, new BigDecimal("200"), null, null, false, false, null, UUID.randomUUID(), null));

        assertThat(product.getAverageUnitCost()).isEqualByComparingTo("150.000000");
        assertThat(product.getStockOnHand()).isEqualByComparingTo("10.000");
        assertThat(result.inventoryValueDelta()).isEqualByComparingTo("1000.00");
        assertThat(result.unitCostSnapshot()).isEqualByComparingTo("200.000000");
        assertThat(result.calculatedTotal()).isEqualByComparingTo("1000.00");
        assertThat(result.averageUnitCostAfter()).isEqualByComparingTo("150.000000");
        assertThat(result.inventoryValueAfter()).isEqualByComparingTo("1500.00");
        verify(financeService).createInventoryPurchase(anyLong(), anyLong(), argThat(request -> request.amount().compareTo(new BigDecimal("1000.00")) == 0
                && request.batchId() == null));
    }

    @Test
    void derivesUnitCostFromLegacyTotalDuringCompatibilityWindow() {
        FarmProduct product = product("bottle", "0");
        when(productRepository.findByIdAndFarmIdForUpdate(10L, 3L)).thenReturn(Optional.of(product));
        when(movementRepository.findByOperationId(any())).thenReturn(Optional.empty());
        when(productRepository.save(any(FarmProduct.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(movementRepository.save(any(InventoryMovement.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(financeService.createInventoryPurchase(anyLong(), anyLong(), any())).thenReturn(
                new FinancialTransactionResponse(89L, null, null, null, null, null, null, null, null,
                        null, null, null, null, null, null, null, null));

        InventoryMovementResponse result = new InventoryService(productRepository, movementRepository, batchService, financeService)
                .stockIn(3L, 7L, 10L, new com.poultryprophet.inventory.dto.StockInRequest(
                        new BigDecimal("4"), null, new BigDecimal("100"), null, null, false, false, null, UUID.randomUUID()));

        assertThat(result.unitCostSnapshot()).isEqualByComparingTo("25.000000");
        assertThat(result.inventoryValueDelta()).isEqualByComparingTo("100.00");
    }

    @Test
    void rejectsConflictingLegacyTotalAndUnitCost() {
        FarmProduct product = product("sachet", "0");
        when(productRepository.findByIdAndFarmIdForUpdate(10L, 3L)).thenReturn(Optional.of(product));

        assertThatThrownBy(() -> new InventoryService(productRepository, movementRepository, batchService, financeService)
                .stockIn(3L, 7L, 10L, new com.poultryprophet.inventory.dto.StockInRequest(
                        new BigDecimal("4"), null, new BigDecimal("25"), null, null, false, false, null,
                        UUID.randomUUID(), new BigDecimal("90"))))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("does not match");
    }

    @Test
    void blocksAddingStockWhenExistingBalanceHasNoUnitCost() {
        FarmProduct product = product("bottle", "3");
        product.setValuationStatus(InventoryValuationStatus.UNVALUED);
        when(productRepository.findByIdAndFarmIdForUpdate(10L, 3L)).thenReturn(Optional.of(product));

        assertThatThrownBy(() -> new InventoryService(productRepository, movementRepository, batchService, financeService)
                .stockIn(3L, 7L, 10L, new com.poultryprophet.inventory.dto.StockInRequest(
                        new BigDecimal("2"), null, new BigDecimal("15"), null, null, false, false, null,
                        UUID.randomUUID(), null)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("no recorded unit cost");
    }

    @Test
    void recordsFreeStockWithoutCreatingCashExpense() {
        FarmProduct product = product("dose", "0");
        when(productRepository.findByIdAndFarmIdForUpdate(10L, 3L)).thenReturn(Optional.of(product));
        when(movementRepository.findByOperationId(any())).thenReturn(Optional.empty());
        when(productRepository.save(any(FarmProduct.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(movementRepository.save(any(InventoryMovement.class))).thenAnswer(invocation -> invocation.getArgument(0));

        InventoryMovementResponse result = new InventoryService(productRepository, movementRepository, batchService, financeService)
                .stockIn(3L, 7L, 10L, new com.poultryprophet.inventory.dto.StockInRequest(
                        new BigDecimal("10"), null, null, null, null, false, true, null, UUID.randomUUID(), null));

        assertThat(result.costStatus()).isEqualTo(InventoryValuationStatus.FREE.name());
        assertThat(result.inventoryValueDelta()).isEqualByComparingTo("0.00");
        verifyNoInteractions(financeService);
    }

    @Test
    void rejectsOperationIdFromAnotherFarm() {
        UUID id = UUID.randomUUID();
        InventoryMovement existing = new InventoryMovement();
        existing.setFarmId(99L);
        existing.setFarmProductId(10L);
        existing.setMovementType(InventoryMovementType.ADJUSTMENT_IN);
        when(movementRepository.findByOperationId(id)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> new InventoryService(productRepository, movementRepository, batchService, financeService)
                .adjust(3L, 7L, 10L, new com.poultryprophet.inventory.dto.InventoryAdjustmentRequest(
                        BigDecimal.ONE, "count", null, id)))
                .isInstanceOf(com.poultryprophet.common.ConflictException.class);
    }

    @Test
    void rejectsAdjustmentReplayWithDifferentQuantity() {
        UUID id = UUID.randomUUID();
        InventoryMovement existing = new InventoryMovement();
        existing.setFarmId(3L);
        existing.setFarmProductId(10L);
        existing.setMovementType(InventoryMovementType.ADJUSTMENT_IN);
        existing.setQuantityDelta(new BigDecimal("1.000"));
        existing.setReason("count");
        when(movementRepository.findByOperationId(id)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> new InventoryService(productRepository, movementRepository, batchService, financeService)
                .adjust(3L, 7L, 10L, new com.poultryprophet.inventory.dto.InventoryAdjustmentRequest(
                        new BigDecimal("2"), "count", null, id)))
                .isInstanceOf(com.poultryprophet.common.ConflictException.class);
    }

    @Test
    void adjustmentReplayTreatsEquivalentDecimalScalesAsEqual() {
        UUID id = UUID.randomUUID();
        InventoryMovement existing = new InventoryMovement();
        existing.setId(50L);
        existing.setFarmId(3L);
        existing.setFarmProductId(10L);
        existing.setMovementType(InventoryMovementType.ADJUSTMENT_IN);
        existing.setQuantityDelta(new BigDecimal("1.000"));
        existing.setReason("count");
        when(movementRepository.findByOperationId(id)).thenReturn(Optional.of(existing));

        var result = new InventoryService(productRepository, movementRepository, batchService, financeService)
                .adjust(3L, 7L, 10L, new com.poultryprophet.inventory.dto.InventoryAdjustmentRequest(
                        new BigDecimal("1.0"), "count", null, id));
        assertThat(result.id()).isEqualTo(50L);
        verifyNoInteractions(productRepository, financeService);
    }

    @Test
    void paidStockInCreatesSystemExpenseThroughRealFinanceService() {
        var financialRepository = mock(com.poultryprophet.finance.FinancialTransactionRepository.class);
        var incubationService = mock(com.poultryprophet.incubation.IncubationService.class);
        var realFinance = new FinanceService(financialRepository, batchService, incubationService, movementRepository);
        FarmProduct product = product("bag", "0");
        when(productRepository.findByIdAndFarmIdForUpdate(10L, 3L)).thenReturn(Optional.of(product));
        when(financialRepository.save(any())).thenAnswer(invocation -> {
            var transaction = (com.poultryprophet.finance.FinancialTransaction) invocation.getArgument(0);
            transaction.setId(88L);
            return transaction;
        });
        when(movementRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        UUID id = UUID.randomUUID();
        var result = new InventoryService(productRepository, movementRepository, batchService, realFinance)
                .stockIn(3L, 7L, 10L, new com.poultryprophet.inventory.dto.StockInRequest(
                        new BigDecimal("2"), null, new BigDecimal("15"), null, "Supplier", false, false, null, id, null));
        assertThat(result.financialTransactionId()).isEqualTo(88L);
        assertThat(product.getStockOnHand()).isEqualByComparingTo("2");
        verify(financialRepository).save(argThat(tx -> tx.getFarmId().equals(3L)
                && tx.getSourceType().equals("INVENTORY_PURCHASE")
                && tx.getSourceOperationId().equals(id.toString())
                && tx.getAmount().compareTo(new BigDecimal("30.00")) == 0));
    }

    private static FarmProduct product(String unit, String stock) {
        FarmProduct product = new FarmProduct();
        product.setId(10L);
        product.setFarmId(3L);
        product.setProductType(com.poultryprophet.input.InputProductType.OTHER);
        product.setStockUnit(unit);
        product.setStockOnHand(new BigDecimal(stock));
        product.setActive(true);
        product.setAllowFractionalQuantity(true);
        return product;
    }
}
