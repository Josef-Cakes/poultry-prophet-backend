package com.poultryprophet.dashboard;

import com.poultryprophet.alert.AlertRepository;
import com.poultryprophet.batch.Batch;
import com.poultryprophet.batch.BatchRepository;
import com.poultryprophet.batch.BatchService;
import com.poultryprophet.batch.BatchStatus;
import com.poultryprophet.batch.dto.BatchResponse;
import com.poultryprophet.dashboard.dto.BatchDashboardResponse;
import com.poultryprophet.selectionreview.SelectionReviewPayload;
import com.poultryprophet.selectionreview.SelectionReviewService;
import com.poultryprophet.user.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 18);

    @Mock private BatchRepository batchRepository;
    @Mock private BatchService batchService;
    @Mock private AlertRepository alertRepository;
    @Mock private SelectionReviewService selectionReviewService;

    private DashboardService service;
    private Batch batch;

    @BeforeEach
    void setUp() {
        service = new DashboardService(batchRepository, batchService, alertRepository,
                selectionReviewService, Clock.fixed(Instant.parse("2026-11-18T00:00:00Z"), ZoneOffset.UTC));
        batch = new Batch();
        batch.setId(7L);
        batch.setFarmId(1L);
        batch.setName("October load");
        batch.setInitialPopulation(30);
        batch.setCurrentPopulation(20);
        batch.setStartDate(TODAY);
        batch.setHatchDate(TODAY);
        batch.setStatus(BatchStatus.ACTIVE);

        when(batchRepository.findByFarmIdAndStatusNotOrderByCreatedAtDesc(1L, BatchStatus.ARCHIVED))
                .thenReturn(List.of(batch));
        when(batchService.responseFor(batch)).thenReturn(batchResponse());
        when(alertRepository.findByBatchIdAndAcknowledgedAtIsNullOrderByCreatedAtDesc(7L)).thenReturn(List.of());
    }

    @Test
    void includesRecordedBatchCostForManagers() {
        when(selectionReviewService.preview(7L, 1L, TODAY, TODAY, TODAY, true))
                .thenReturn(payload(finance("PHP", new BigDecimal("12450"), false, 0)));

        BatchDashboardResponse response = service.listForFarm(1L, Role.MANAGER).getFirst();

        assertThat(response.summary().finance()).isNotNull();
        assertThat(response.summary().finance().hasCostRecords()).isTrue();
        assertThat(response.summary().finance().recordedBatchCost()).isEqualByComparingTo("12450");
        assertThat(response.summary().finance().currency()).isEqualTo("PHP");
        assertThat(response.summary().finance().productCostComplete()).isTrue();
        verify(selectionReviewService).preview(7L, 1L, TODAY, TODAY, TODAY, true);
    }

    @Test
    void omitsFinanceFromHandlerDashboardPayloads() {
        when(selectionReviewService.preview(7L, 1L, TODAY, TODAY, TODAY, false))
                .thenReturn(payload(null));

        BatchDashboardResponse response = service.listForFarm(1L, Role.HANDLER).getFirst();

        assertThat(response.summary().finance()).isNull();
        verify(selectionReviewService).preview(7L, 1L, TODAY, TODAY, TODAY, false);
    }

    @Test
    void preservesMixedCurrenciesWithoutAddingThemTogether() {
        SelectionReviewPayload.FinanceSummary.CurrencyTotal php = currencyTotal("PHP", new BigDecimal("120"), false);
        SelectionReviewPayload.FinanceSummary.CurrencyTotal usd = currencyTotal("USD", new BigDecimal("10"), false);
        SelectionReviewPayload.FinanceSummary finance = new SelectionReviewPayload.FinanceSummary(
                TODAY, TODAY, null, null, null, 0, null, null, null, null, null,
                2, false, 0, "Currencies are separate.", List.of(php, usd));
        when(selectionReviewService.preview(7L, 1L, TODAY, TODAY, TODAY, true)).thenReturn(payload(finance));

        BatchDashboardResponse response = service.listForFarm(1L, Role.MANAGER).getFirst();

        assertThat(response.summary().finance().hasCostRecords()).isTrue();
        assertThat(response.summary().finance().recordedBatchCost()).isNull();
        assertThat(response.summary().finance().currency()).isNull();
    }

    private BatchResponse batchResponse() {
        return new BatchResponse(7L, 1L, "October load", 30, 20, "VALID", null,
                TODAY, TODAY, TODAY, null, null, null, null, 1L, "Brooding", true,
                BatchStatus.ACTIVE, List.of(), Instant.EPOCH, null, null, null, null);
    }

    private SelectionReviewPayload payload(SelectionReviewPayload.FinanceSummary finance) {
        return new SelectionReviewPayload(
                "Batch Selection Review Report", "", "selection-review-v3", TODAY, TODAY, TODAY,
                new SelectionReviewPayload.BatchOverview(7L, "October load", null, null, 30, 20,
                        "20 / 30", 1, "Brooding", TODAY, null),
                new SelectionReviewPayload.PopulationSummary(30, 20, 20, false, null, 0, null,
                        0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, Map.of()),
                List.of(), List.of(), null, finance, List.of(), null, null, null);
    }

    private SelectionReviewPayload.FinanceSummary finance(String currency, BigDecimal cost,
                                                            boolean incomplete, long unvaluedUsageCount) {
        SelectionReviewPayload.FinanceSummary.CurrencyTotal total = currencyTotal(currency, cost, incomplete);
        return new SelectionReviewPayload.FinanceSummary(TODAY, TODAY, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, 0, cost, cost.negate(), BigDecimal.ZERO, cost, cost.negate(), 1,
                incomplete, unvaluedUsageCount, "", List.of(total));
    }

    private SelectionReviewPayload.FinanceSummary.CurrencyTotal currencyTotal(String currency, BigDecimal cost,
                                                                                boolean incomplete) {
        return new SelectionReviewPayload.FinanceSummary.CurrencyTotal(currency, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, 0, cost, cost.negate(), BigDecimal.ZERO, cost,
                cost.negate(), 1, incomplete);
    }
}
