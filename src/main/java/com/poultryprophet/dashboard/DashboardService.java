package com.poultryprophet.dashboard;

import com.poultryprophet.alert.Alert;
import com.poultryprophet.alert.AlertRepository;
import com.poultryprophet.alert.Severity;
import com.poultryprophet.batch.Batch;
import com.poultryprophet.batch.BatchHandlerAssignmentRepository;
import com.poultryprophet.batch.BatchRepository;
import com.poultryprophet.batch.BatchService;
import com.poultryprophet.batch.BatchStatus;
import com.poultryprophet.batch.dto.BatchResponse;
import com.poultryprophet.dashboard.dto.BatchDashboardResponse;
import com.poultryprophet.dashboard.dto.BatchDashboardSummary;
import com.poultryprophet.selectionreview.SelectionReviewPayload;
import com.poultryprophet.selectionreview.SelectionReviewService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

/** Builds one farm-scoped payload for the dashboard batch cards. */
@Service
public class DashboardService {

    private final BatchRepository batchRepository;
    private final BatchService batchService;
    private final BatchHandlerAssignmentRepository assignmentRepository;
    private final AlertRepository alertRepository;
    private final SelectionReviewService selectionReviewService;
    private final Clock applicationClock;

    public DashboardService(BatchRepository batchRepository,
                            BatchService batchService,
                            BatchHandlerAssignmentRepository assignmentRepository,
                            AlertRepository alertRepository,
                            SelectionReviewService selectionReviewService,
                            Clock applicationClock) {
        this.batchRepository = batchRepository;
        this.batchService = batchService;
        this.assignmentRepository = assignmentRepository;
        this.alertRepository = alertRepository;
        this.selectionReviewService = selectionReviewService;
        this.applicationClock = applicationClock;
    }

    @Transactional(readOnly = true)
    public List<BatchDashboardResponse> listForFarm(Long farmId) {
        LocalDate today = LocalDate.now(applicationClock);
        return batchRepository.findByFarmIdAndStatusNotOrderByCreatedAtDesc(farmId, BatchStatus.ARCHIVED)
                .stream()
                .map(batch -> toResponse(batch, farmId, today))
                .toList();
    }

    private BatchDashboardResponse toResponse(Batch batch, Long farmId, LocalDate today) {
        BatchService.StageView stageView = batchService.resolveStage(batch);
        BatchResponse batchResponse = BatchResponse.from(
                batch,
                assignmentRepository.findHandlerUserIdsByBatchId(batch.getId()),
                stageView.stage(),
                stageView.auto());

        SelectionReviewPayload payload = selectionReviewService.preview(
                batch.getId(), farmId, batch.getStartDate(), today, today, false);
        SelectionReviewPayload.PopulationSummary population = payload.population();
        long otherChanges = population.accidentalDeaths()
                + population.predation()
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
                otherChanges,
                payload.healthEvents().size(),
                activeAlerts.size(),
                highestSeverity,
                payload.batch().lastRecordedEventDate());
        return new BatchDashboardResponse(batchResponse, summary);
    }

    private int severityRank(Severity severity) {
        return switch (severity) {
            case INFO -> 1;
            case WARNING -> 2;
            case CRITICAL -> 3;
        };
    }
}
