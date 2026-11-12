package com.poultryprophet.analytics;

import com.poultryprophet.batch.BatchRepository;
import com.poultryprophet.event.BatchEventRepository;
import com.poultryprophet.finance.FinanceService;
import com.poultryprophet.finance.dto.FinanceAnalyticsResponse;
import com.poultryprophet.finance.dto.FinanceSummaryResponse;
import com.poultryprophet.incubation.IncubationCycleRepository;
import com.poultryprophet.input.FarmInputLogRepository;
import com.poultryprophet.population.PopulationProjectionService;
import com.poultryprophet.task.HandlerTaskRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FinanceSummaryCurrencyTest {
    private static final LocalDate START = LocalDate.of(2026, 10, 1);
    private static final LocalDate END = LocalDate.of(2026, 10, 24);

    @Test
    void operationsReturnsSeparateCurrenciesAndNoMixedLegacyAmounts() {
        FinanceService finance = mock(FinanceService.class);
        var totals = List.of(new FinanceSummaryResponse.CurrencyTotal("PHP", bd("100"), bd("0"), bd("100")),
                new FinanceSummaryResponse.CurrencyTotal("USD", bd("0"), bd("30"), bd("-30")));
        when(finance.summary(42L, START, END)).thenReturn(new FinanceSummaryResponse(START, END, null, null, null, 2, totals));
        var service = new OperationsAnalyticsService(mock(IncubationCycleRepository.class), mock(HandlerTaskRepository.class),
                mock(FarmInputLogRepository.class), finance);

        var result = service.summarize(42L, START, END).finance();
        assertThat(result.currency()).isNull();
        assertThat(result.net()).isNull();
        assertThat(result.byCurrency()).extracting(value -> value.currency()).containsExactly("PHP", "USD");
        assertThat(result.byCurrency().get(1).net()).isEqualTo("-30");
    }

    @Test
    void operationsLabelsSingleForeignCurrencyCorrectly() {
        FinanceService finance = mock(FinanceService.class);
        var totals = List.of(new FinanceSummaryResponse.CurrencyTotal("USD", bd("100"), bd("30"), bd("70")));
        when(finance.summary(42L, START, END)).thenReturn(new FinanceSummaryResponse(START, END, bd("100"), bd("30"), bd("70"), 2, totals));
        var service = new OperationsAnalyticsService(mock(IncubationCycleRepository.class), mock(HandlerTaskRepository.class),
                mock(FarmInputLogRepository.class), finance);

        var result = service.summarize(42L, START, END).finance();
        assertThat(result.currency()).isEqualTo("USD");
        assertThat(result.net()).isEqualTo("70");
    }

    @Test
    void farmOverviewReturnsCurrencyGroupsWithoutFabricatingAggregate() {
        FinanceService finance = mock(FinanceService.class);
        var php = currency("PHP", "100", "0");
        var usd = currency("USD", "0", "30");
        when(finance.analyticsForFarmScope(42L, Set.of(), START, END)).thenReturn(new FinanceAnalyticsResponse(
                "FARM", null, null, START, END, null, null, List.of(), List.of(),
                new FinanceAnalyticsResponse.Limitations(false, "Separate currencies"), List.of(php, usd)));
        var service = new FarmSummaryAnalyticsService(mock(BatchRepository.class), mock(BatchEventRepository.class), finance,
                mock(HandlerTaskRepository.class), mock(IncubationCycleRepository.class), mock(FarmInputLogRepository.class),
                mock(PopulationProjectionService.class), "Asia/Manila");

        var result = service.summarize(42L, "FARM", null, START, END, "REAL", null).finance();
        assertThat(result.currency()).isNull();
        assertThat(result.net()).isNull();
        assertThat(result.byCurrency()).extracting(value -> value.currency()).containsExactly("PHP", "USD");
        assertThat(result.byCurrency().get(1).net()).isEqualTo("-30.00");
        assertThat(result.transactionCount()).isEqualTo(2);
    }

    private static FinanceAnalyticsResponse.CurrencyReport currency(String currency, String income, String expense) {
        var in = bd(income); var out = bd(expense);
        return new FinanceAnalyticsResponse.CurrencyReport(currency,
                new FinanceAnalyticsResponse.Totals(in, bd("0"), in, 0, out, in.subtract(out), bd("0"), out,
                        in.subtract(out), true, 1, 0), List.of(), List.of());
    }

    private static BigDecimal bd(String value) { return new BigDecimal(value); }
}
