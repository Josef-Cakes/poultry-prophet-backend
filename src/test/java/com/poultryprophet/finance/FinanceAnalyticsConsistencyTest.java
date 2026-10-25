package com.poultryprophet.finance;

import com.poultryprophet.batch.BatchService;
import com.poultryprophet.incubation.IncubationService;
import com.poultryprophet.inventory.InventoryMovementRepository;
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
