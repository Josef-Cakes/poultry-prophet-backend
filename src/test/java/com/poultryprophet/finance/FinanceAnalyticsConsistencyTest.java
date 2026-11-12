package com.poultryprophet.finance;

import com.poultryprophet.batch.BatchService;
import com.poultryprophet.batch.Batch;
import com.poultryprophet.incubation.IncubationService;
import com.poultryprophet.inventory.InventoryMovementRepository;
import com.poultryprophet.inventory.InventoryMovement;
import com.poultryprophet.inventory.InventoryMovementType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
class FinanceAnalyticsConsistencyTest {
    @Mock private FinancialTransactionRepository repository;
    @Mock private BatchService batchService;
    @Mock private IncubationService incubationService;
    @Mock private InventoryMovementRepository inventoryMovementRepository;

    private FinanceService service;

    @BeforeEach
    void setUp() {
        service = new FinanceService(repository, batchService, incubationService, inventoryMovementRepository);
    }

    @Test
    void farmScopeAnalyticsUsesPostedCashTotalsAndExcludesVoidedRecords() {
        FinancialTransaction sale = transaction(1L, FinanceTransactionType.INCOME, "Bird sale", "1000.00", "SALE_EVENT", FinanceTransactionStatus.POSTED);
        FinancialTransaction expense = transaction(2L, FinanceTransactionType.EXPENSE, "Feed", "300.00", "MANUAL", FinanceTransactionStatus.POSTED);
        FinancialTransaction voided = transaction(3L, FinanceTransactionType.EXPENSE, "Feed", "900.00", "MANUAL", FinanceTransactionStatus.VOIDED);
        when(repository.findAll(any(Specification.class), any(Sort.class))).thenReturn(List.of(sale, expense, voided));

        var result = service.analyticsForFarmScope(42L, Set.of(7L), LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 24));

        assertThat(result.totals().recordedIncome()).isEqualByComparingTo("1000.00");
        assertThat(result.totals().saleIncome()).isEqualByComparingTo("1000.00");
        assertThat(result.totals().recordedExpense()).isEqualByComparingTo("300.00");
        assertThat(result.totals().recordedNetCashFlow()).isEqualByComparingTo("700.00");
        assertThat(result.totals().postedTransactionCount()).isEqualTo(2);
        assertThat(result.totals().voidedTransactionCount()).isEqualTo(1);
    }

    @Test
    void separatesCurrenciesWithoutProvidingMixedLegacyTotals() {
        var php = transaction(1L, FinanceTransactionType.INCOME, "Sale", "100", "SALE_EVENT", FinanceTransactionStatus.POSTED);
        var usd = transaction(2L, FinanceTransactionType.EXPENSE, "Feed", "30", "MANUAL", FinanceTransactionStatus.POSTED);
        usd.setCurrency("USD");
        var voided = transaction(3L, FinanceTransactionType.EXPENSE, "Feed", "90", "MANUAL", FinanceTransactionStatus.VOIDED);
        voided.setCurrency("EUR");
        when(repository.findAll(any(Specification.class), any(Sort.class))).thenReturn(List.of(php, usd, voided));

        var result = service.analytics(42L, null, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 24));

        assertThat(result.currency()).isNull();
        assertThat(result.totals()).isNull();
        assertThat(result.series()).isEmpty();
        assertThat(result.byCurrency()).extracting(report -> report.currency()).containsExactly("PHP", "USD");
        assertThat(result.byCurrency().get(0).totals().recordedIncome()).isEqualByComparingTo("100");
        assertThat(result.byCurrency().get(1).totals().recordedExpense()).isEqualByComparingTo("30");
        assertThat(result.byCurrency().get(1).categoryTotals().get(0).amount()).isEqualByComparingTo("30");
    }

    @Test
    void summarySeparatesPostedCurrencies() {
        var php = transaction(1L, FinanceTransactionType.INCOME, "Sale", "100", "MANUAL", FinanceTransactionStatus.POSTED);
        var usd = transaction(2L, FinanceTransactionType.EXPENSE, "Feed", "30", "MANUAL", FinanceTransactionStatus.POSTED);
        usd.setCurrency("USD");
        when(repository.findByFarmIdAndTransactionDateBetweenOrderByTransactionDateAsc(any(), any(), any())).thenReturn(List.of(php, usd));

        var result = service.summary(42L, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 24));

        assertThat(result.income()).isNull();
        assertThat(result.net()).isNull();
        assertThat(result.byCurrency()).extracting(report -> report.currency()).containsExactly("PHP", "USD");
        assertThat(result.byCurrency().get(1).net()).isEqualByComparingTo("-30");
    }

    @Test
    void retainsPhpInventoryCostForSingleCurrencyBatch() {
        var cash = transaction(1L, FinanceTransactionType.INCOME, "Sale", "100", "SALE_EVENT", FinanceTransactionStatus.POSTED);
        var movement = new InventoryMovement();
        movement.setMovementType(InventoryMovementType.USAGE);
        movement.setInventoryValueDelta(new BigDecimal("-40"));
        when(batchService.requireBatch(7L, 42L)).thenReturn(mock(Batch.class));
        when(repository.findAll(any(Specification.class), any(Sort.class))).thenReturn(List.of(cash));
        when(inventoryMovementRepository.findByFarmIdAndBatchIdAndOccurredAtBetweenOrderByOccurredAtAsc(any(), any(), any(), any()))
                .thenReturn(List.of(movement));

        var result = service.analytics(42L, 7L, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 24));

        assertThat(result.currency()).isEqualTo("PHP");
        assertThat(result.totals().recordedNetCashFlow()).isEqualByComparingTo("100");
        assertThat(result.totals().productsConsumedCost()).isEqualByComparingTo("40");
        assertThat(result.totals().totalRecordedBatchCost()).isEqualByComparingTo("40");
        assertThat(result.totals().recordedContribution()).isEqualByComparingTo("60");
    }

    @Test
    void normalizesCurrencyCodesAndKeepsSingleCurrencyFields() {
        var first = transaction(1L, FinanceTransactionType.INCOME, "Sale", "100", "MANUAL", FinanceTransactionStatus.POSTED);
        var second = transaction(2L, FinanceTransactionType.EXPENSE, "Feed", "30", "MANUAL", FinanceTransactionStatus.POSTED);
        first.setCurrency(" usd ");
        second.setCurrency("USD");
        when(repository.findAll(any(Specification.class), any(Sort.class))).thenReturn(List.of(first, second));

        var result = service.analytics(42L, null, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 24));

        assertThat(result.currency()).isEqualTo("USD");
        assertThat(result.totals().recordedNetCashFlow()).isEqualByComparingTo("70");
        assertThat(result.series().get(0).netChange()).isEqualByComparingTo("70");
        assertThat(result.byCurrency()).hasSize(1);
    }

    @Test
    void providesSeparateCurrencyReportsForFarmOverview() {
        var php = transaction(1L, FinanceTransactionType.INCOME, "Sale", "100", "MANUAL", FinanceTransactionStatus.POSTED);
        var usd = transaction(2L, FinanceTransactionType.EXPENSE, "Feed", "30", "MANUAL", FinanceTransactionStatus.POSTED);
        usd.setCurrency("USD");
        when(repository.findAll(any(Specification.class), any(Sort.class))).thenReturn(List.of(php, usd));

        var result = service.analyticsForFarmScope(42L, Set.of(7L), LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 24));
        assertThat(result.totals()).isNull();
        assertThat(result.byCurrency()).extracting(report -> report.currency()).containsExactly("PHP", "USD");
    }

    @Test
    void attributesLegacyInventoryCostsOnlyToPhpWhenCashIsUsd() {
        var cash = transaction(1L, FinanceTransactionType.INCOME, "Sale", "100", "SALE_EVENT", FinanceTransactionStatus.POSTED);
        cash.setCurrency("USD");
        var movement = new InventoryMovement();
        movement.setMovementType(InventoryMovementType.USAGE);
        movement.setInventoryValueDelta(new BigDecimal("-40"));
        when(batchService.requireBatch(7L, 42L)).thenReturn(mock(Batch.class));
        when(repository.findAll(any(Specification.class), any(Sort.class))).thenReturn(List.of(cash));
        when(inventoryMovementRepository.findByFarmIdAndBatchIdAndOccurredAtBetweenOrderByOccurredAtAsc(any(), any(), any(), any()))
                .thenReturn(List.of(movement));

        var result = service.analytics(42L, 7L, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 24));
        assertThat(result.totals()).isNull();
        assertThat(result.byCurrency()).extracting(report -> report.currency()).containsExactly("PHP", "USD");
        assertThat(result.byCurrency().get(0).totals().productsConsumedCost()).isEqualByComparingTo("40");
        assertThat(result.byCurrency().get(1).totals().recordedIncome()).isEqualByComparingTo("100");
        assertThat(result.byCurrency().get(1).totals().recordedContribution()).isNull();
    }

    private FinancialTransaction transaction(Long id, FinanceTransactionType type, String category,
                                             String amount, String sourceType, FinanceTransactionStatus status) {
        FinancialTransaction transaction = new FinancialTransaction();
        transaction.setId(id);
        transaction.setFarmId(42L);
        transaction.setBatchId(7L);
        transaction.setTransactionDate(LocalDate.of(2026, 10, 9));
        transaction.setType(type);
        transaction.setCategory(category);
        transaction.setAmount(new BigDecimal(amount));
        transaction.setCurrency("PHP");
        transaction.setSourceType(sourceType);
        transaction.setStatus(status);
        transaction.setEnteredBy(1L);
        return transaction;
    }
}
