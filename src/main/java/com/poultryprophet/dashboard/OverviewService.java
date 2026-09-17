package com.poultryprophet.dashboard;

import com.poultryprophet.alert.AlertRepository;
import com.poultryprophet.alert.dto.AlertResponse;
import com.poultryprophet.analytics.IndicatorRepository;
import com.poultryprophet.analytics.ThresholdConfig;
import com.poultryprophet.analytics.ThresholdConfigRepository;
import com.poultryprophet.analytics.dto.IndicatorResponse;
import com.poultryprophet.batch.Batch;
import com.poultryprophet.batch.BatchHandlerAssignmentRepository;
import com.poultryprophet.batch.BatchService;
import com.poultryprophet.batch.dto.BatchResponse;
import com.poultryprophet.dashboard.dto.BatchOverviewResponse;
import com.poultryprophet.record.DailyRecordRepository;
import com.poultryprophet.record.dto.DailyRecordResponse;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.HashMap;
import java.util.Map;

/** SDD 3.1: aggregation logic for the batch dashboard. */
@Service
public class OverviewService {

    private static final int RECENT_RECORD_LIMIT = 30;

    private final BatchService batchService;
    private final BatchHandlerAssignmentRepository assignmentRepository;
    private final IndicatorRepository indicatorRepository;
    private final DailyRecordRepository recordRepository;
    private final AlertRepository alertRepository;
    private final ThresholdConfigRepository thresholdRepository;

    public OverviewService(BatchService batchService,
                           BatchHandlerAssignmentRepository assignmentRepository,
                           IndicatorRepository indicatorRepository,
                           DailyRecordRepository recordRepository,
                           AlertRepository alertRepository,
                           ThresholdConfigRepository thresholdRepository) {
        this.batchService = batchService;
        this.assignmentRepository = assignmentRepository;
        this.indicatorRepository = indicatorRepository;
        this.recordRepository = recordRepository;
        this.alertRepository = alertRepository;
        this.thresholdRepository = thresholdRepository;
    }

    @Transactional(readOnly = true)
    public BatchOverviewResponse getOverview(Long batchId, Long farmId) {
        Batch batch = batchService.requireBatch(batchId, farmId);

        BatchService.StageView stageView = batchService.resolveStage(batch);
        BatchResponse batchResponse = BatchResponse.from(
                batch, assignmentRepository.findHandlerUserIdsByBatchId(batchId),
                stageView.stage(), stageView.auto());

        IndicatorResponse latestIndicator = indicatorRepository
                .findByBatchIdOrderByObservationDateDesc(batchId, PageRequest.of(0, 1))
                .stream()
                .findFirst()
                .map(indicator -> IndicatorResponse.from(indicator, effectiveThresholds(farmId)))
                .orElse(null);

        List<DailyRecordResponse> recentRecords = recordRepository
                .findByBatchIdOrderByRecordDateDesc(batchId, PageRequest.of(0, RECENT_RECORD_LIMIT))
                .stream()
                .map(DailyRecordResponse::from)
                .toList();

        List<AlertResponse> activeAlerts = alertRepository
                .findByBatchIdAndAcknowledgedAtIsNullOrderByCreatedAtDesc(batchId)
                .stream()
                .map(AlertResponse::from)
                .toList();

        return new BatchOverviewResponse(batchResponse, latestIndicator, recentRecords, activeAlerts);
    }

    private Map<String, ThresholdConfig> effectiveThresholds(Long farmId) {
        Map<String, ThresholdConfig> thresholds = new HashMap<>();
        for (String metric : List.of("BHI", "BSI", "WFR")) {
            thresholdRepository.findEffective(farmId, metric).ifPresent(value -> thresholds.put(metric, value));
        }
        return thresholds;
    }
}
