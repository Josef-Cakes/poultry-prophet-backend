package com.poultryprophet.selectionreview;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.poultryprophet.finance.*;
import com.poultryprophet.inventory.*;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SelectionReviewCurrencyTest {
    private static final LocalDate START = LocalDate.of(2026, 1, 1), END = LocalDate.of(2026, 1, 31);

    @Test void separatesCurrenciesAndKeepsInventoryInPhp() throws Exception {
        var finance = mock(FinancialTransactionRepository.class);
        var inventory = mock(InventoryMovementRepository.class);
        when(finance.findByFarmIdAndBatchIdAndTransactionDateBetweenOrderByTransactionDateAsc(1L, 2L, START, END))
                .thenReturn(List.of(tx("PHP", "100", FinanceTransactionType.INCOME, "SALE_EVENT", FinanceTransactionStatus.POSTED),
                        tx("usd", "30", FinanceTransactionType.INCOME, null, FinanceTransactionStatus.POSTED),
                        tx("USD", "5", FinanceTransactionType.EXPENSE, null, FinanceTransactionStatus.POSTED),
                        tx("USD", "999", FinanceTransactionType.INCOME, null, FinanceTransactionStatus.VOIDED)));
        var usage = new InventoryMovement(); usage.setMovementType(InventoryMovementType.USAGE); usage.setInventoryValueDelta(new BigDecimal("-20"));
        when(inventory.findByFarmIdAndBatchIdAndOccurredAtBetweenOrderByOccurredAtAsc(eq(1L), eq(2L), any(), any())).thenReturn(List.of(usage));
        var service = new SelectionReviewService(null, null, null, null, finance, null, new ObjectMapper(), null, null, null, null, inventory);
        var method = SelectionReviewService.class.getDeclaredMethod("financeSummary", Long.class, Long.class, LocalDate.class, LocalDate.class);
        method.setAccessible(true);
        var summary = (SelectionReviewPayload.FinanceSummary) method.invoke(service, 1L, 2L, START, END);
        assertThat(summary.recordedIncome()).isNull();
        assertThat(summary.currencyTotals()).hasSize(2);
        var php = summary.currencyTotals().stream().filter(x -> x.currency().equals("PHP")).findFirst().orElseThrow();
        var usd = summary.currencyTotals().stream().filter(x -> x.currency().equals("USD")).findFirst().orElseThrow();
        assertThat(php.recordedContribution()).isEqualByComparingTo("80");
        assertThat(php.saleTransactionCount()).isEqualTo(1);
        assertThat(usd.recordedNetCashFlow()).isEqualByComparingTo("25");
        assertThat(usd.productsConsumedCost()).isNull();
        assertThat(usd.recordedContribution()).isNull();
    }

    @Test void preservesPhpLegacyFieldsAndDeserializesHistoricalSummary() throws Exception {
        var finance = mock(FinancialTransactionRepository.class);
        when(finance.findByFarmIdAndBatchIdAndTransactionDateBetweenOrderByTransactionDateAsc(1L, 2L, START, END))
                .thenReturn(List.of(tx("PHP", "100", FinanceTransactionType.INCOME, "SALE_EVENT", FinanceTransactionStatus.POSTED)));
        var service = new SelectionReviewService(null, null, null, null, finance, null, new ObjectMapper());
        var method = SelectionReviewService.class.getDeclaredMethod("financeSummary", Long.class, Long.class, LocalDate.class, LocalDate.class);
        method.setAccessible(true);
        var summary = (SelectionReviewPayload.FinanceSummary) method.invoke(service, 1L, 2L, START, END);
        assertThat(summary.recordedIncome()).isEqualByComparingTo("100");
        assertThat(summary.currencyTotals()).hasSize(1);
        var historical = new ObjectMapper().readValue("{\"recordedIncome\":100,\"postedTransactionCount\":1}", SelectionReviewPayload.FinanceSummary.class);
        assertThat(historical.currencyTotals()).isNull();
    }

    @Test void nonPhpOnlyNeverPopulatesLegacyPhpFieldsAndExcludesPurchases() throws Exception {
        var finance = mock(FinancialTransactionRepository.class);
        when(finance.findByFarmIdAndBatchIdAndTransactionDateBetweenOrderByTransactionDateAsc(1L, 2L, START, END))
                .thenReturn(List.of(tx("USD", "80", FinanceTransactionType.INCOME, null, FinanceTransactionStatus.POSTED),
                        tx("USD", "10", FinanceTransactionType.EXPENSE, null, FinanceTransactionStatus.POSTED),
                        tx("USD", "50", FinanceTransactionType.EXPENSE, "INVENTORY_PURCHASE", FinanceTransactionStatus.POSTED)));
        var service = new SelectionReviewService(null, null, null, null, finance, null, new ObjectMapper());
        var method = SelectionReviewService.class.getDeclaredMethod("financeSummary", Long.class, Long.class, LocalDate.class, LocalDate.class);
        method.setAccessible(true);
        var summary = (SelectionReviewPayload.FinanceSummary) method.invoke(service, 1L, 2L, START, END);
        assertThat(summary.recordedIncome()).isNull();
        assertThat(summary.currencyTotals()).hasSize(1);
        var usd = summary.currencyTotals().get(0);
        assertThat(usd.currency()).isEqualTo("USD");
        assertThat(usd.recordedExpense()).isEqualByComparingTo("10");
        assertThat(usd.recordedContribution()).isEqualByComparingTo("70");
    }

    private static FinancialTransaction tx(String currency, String amount, FinanceTransactionType type, String source, FinanceTransactionStatus status) {
        var tx = new FinancialTransaction(); tx.setCurrency(currency); tx.setAmount(new BigDecimal(amount)); tx.setType(type); tx.setSourceType(source); tx.setStatus(status); return tx;
    }
}
