package com.poultryprophet.dashboard;

import com.poultryprophet.alert.Alert;
import com.poultryprophet.alert.AlertRepository;
import com.poultryprophet.alert.Severity;
import com.poultryprophet.batch.Batch;
import com.poultryprophet.batch.BatchRepository;
import com.poultryprophet.batch.BatchService;
import com.poultryprophet.batch.BatchStatus;
import com.poultryprophet.batch.dto.BatchResponse;
import com.poultryprophet.dashboard.dto.BatchDashboardResponse;
import com.poultryprophet.dashboard.dto.BatchFinanceSnapshot;
import com.poultryprophet.dashboard.dto.BatchDashboardSummary;
import com.poultryprophet.selectionreview.SelectionReviewPayload;
import com.poultryprophet.selectionreview.SelectionReviewService;
import com.poultryprophet.user.Role;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

/** Builds one farm-scoped payload for the dashboard batch cards. */
@Service
public class DashboardService {

    private final BatchRepository batchRepository;
    private final BatchService batchService;
    private final AlertRepository alertRepository;
    private final SelectionReviewService selectionReviewService;
    private final Clock applicationClock;

    public DashboardService(BatchRepository batchRepository,
                            BatchService batchService,
                            AlertRepository alertRepository,
                            SelectionReviewService selectionReviewService,
                            Clock applicationClock) {
        this.batchRepository = batchRepository;
        this.batchService = batchService;
        this.alertRepository = alertRepository;
        this.selectionReviewService = selectionReviewService;
        this.applicationClock = applicationClock;
    }

    @Transactional(readOnly = true)
    public List<BatchDashboardResponse> listForFarm(Long farmId, Role role) {
        LocalDate today = LocalDate.now(applicationClock);
        boolean includeFinance = role == Role.MANAGER;
        return batchRepository.findByFarmIdAndStatusNotOrderByCreatedAtDesc(farmId, BatchStatus.ARCHIVED)
                .stream()
                .map(batch -> toResponse(batch, farmId, today, includeFinance))
                .toList();
    }

    private BatchDashboardResponse toResponse(Batch batch, Long farmId, LocalDate today, boolean includeFinance) {
        LocalDate periodStart = batch.getHatchDate().isAfter(today) ? today : batch.getHatchDate();
        SelectionReviewPayload payload = selectionReviewService.preview(
                batch.getId(), farmId, periodStart, today, today, includeFinance);
        SelectionReviewPayload.PopulationSummary population = payload.population();
        BatchResponse batchResponse = batchService.responseFor(batch);
        long otherChanges = population.predation()
                + population.missing()
                + population.returned()
                + population.transfersOut()
                + population.transfersIn()
                + population.sales()
                + population.culling()
                + population.countCorrections();

        List<Alert> activeAlerts = alertRepository
                .findByBatchIdAndAcknowledgedAtIsNullOrderByCreatedAtDesc(batch.getId())
                .stream()
                .filter(alert -> alert.getIndicatorType() == null
                        || !List.of("BHI", "BSI", "WFR", "CRS").contains(alert.getIndicatorType()))
                .toList();

        Severity highestSeverity = activeAlerts.stream()
                .map(Alert::getSeverity)
                .filter(value -> value != null)
                .max(Comparator.comparingInt(this::severityRank))
                .orElse(null);

        BatchDashboardSummary summary = new BatchDashboardSummary(
                population.healthRelatedDeaths(),
                population.accidentalDeaths(),
                population.totalDeaths(),
                otherChanges,
                payload.healthEvents().size(),
                activeAlerts.size(),
                highestSeverity,
                payload.batch().lastRecordedEventDate(),
                financeSnapshot(payload.finance(), today));
        return new BatchDashboardResponse(batchResponse, summary);
    }

    private BatchFinanceSnapshot financeSnapshot(SelectionReviewPayload.FinanceSummary finance, LocalDate asOfDate) {
        if (finance == null) return null;

        List<SelectionReviewPayload.FinanceSummary.CurrencyTotal> totals = finance.currencyTotals() == null
                ? List.of()
                : finance.currencyTotals();
        if (totals.size() > 1) {
            boolean hasCost = totals.stream().anyMatch(this::hasRecordedCost) || finance.unvaluedUsageCount() > 0;
            return new BatchFinanceSnapshot(null, hasCost, !finance.recordsMayBeIncomplete(),
                    finance.unvaluedUsageCount(), null, asOfDate);
        }

        if (totals.size() == 1) {
            SelectionReviewPayload.FinanceSummary.CurrencyTotal total = totals.getFirst();
            boolean hasKnownCost = hasRecordedCost(total);
            boolean hasCost = hasKnownCost || finance.unvaluedUsageCount() > 0;
            BigDecimal recordedCost = !hasKnownCost && total.recordsMayBeIncomplete()
                    ? null
                    : total.totalRecordedBatchCost();
            return new BatchFinanceSnapshot(recordedCost, hasCost, !total.recordsMayBeIncomplete(),
                    finance.unvaluedUsageCount(), total.currency(), asOfDate);
        }

        boolean hasKnownCost = nonZero(finance.recordedExpense())
                || nonZero(finance.productsConsumedCost())
                || nonZero(finance.totalRecordedBatchCost());
        boolean hasCost = hasKnownCost || finance.unvaluedUsageCount() > 0;
        BigDecimal recordedCost = !hasKnownCost && finance.recordsMayBeIncomplete()
                ? null
                : finance.totalRecordedBatchCost();
        return new BatchFinanceSnapshot(recordedCost, hasCost, !finance.recordsMayBeIncomplete(),
                finance.unvaluedUsageCount(), "PHP", asOfDate);
    }

    private boolean hasRecordedCost(SelectionReviewPayload.FinanceSummary.CurrencyTotal total) {
        return nonZero(total.recordedExpense())
                || nonZero(total.productsConsumedCost())
                || nonZero(total.totalRecordedBatchCost());
    }

    private boolean nonZero(BigDecimal value) {
        return value != null && value.signum() != 0;
    }

    private int severityRank(Severity severity) {
        return switch (severity) {
            case INFO -> 1;
            case WARNING -> 2;
            case CRITICAL -> 3;
        };
    }
}
