package com.poultryprophet.analytics;

import com.poultryprophet.analytics.dto.IndicatorResponse;
import com.poultryprophet.batch.BatchService;
import com.poultryprophet.common.NotFoundException;
import com.poultryprophet.common.QueryLimits;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.HashMap;

/** Read-side for indicators (SDD 2.1 gauge/sparkline data), with farm scoping. */
@Service
public class IndicatorQueryService {

    private final IndicatorRepository indicatorRepository;
    private final BatchService batchService;
    private final ThresholdConfigRepository thresholdRepository;

    public IndicatorQueryService(IndicatorRepository indicatorRepository, BatchService batchService,
                                 ThresholdConfigRepository thresholdRepository) {
        this.indicatorRepository = indicatorRepository;
        this.batchService = batchService;
        this.thresholdRepository = thresholdRepository;
    }

    @Transactional(readOnly = true)
    public IndicatorResponse latest(Long batchId, Long farmId) {
        batchService.requireBatch(batchId, farmId);
        return indicatorRepository.findByBatchIdOrderByObservationDateDesc(batchId, PageRequest.of(0, 1))
                .stream()
                .findFirst()
                .map(indicator -> IndicatorResponse.from(indicator, effectiveThresholds(farmId)))
                .orElseThrow(() -> new NotFoundException("No indicators computed yet for batch " + batchId));
    }

    @Transactional(readOnly = true)
    public List<IndicatorResponse> recent(Long batchId, Long farmId, int limit) {
        batchService.requireBatch(batchId, farmId);
        return indicatorRepository
                .findByBatchIdOrderByObservationDateDesc(batchId, PageRequest.of(0, QueryLimits.clamp(limit)))
                .stream()
                .map(indicator -> IndicatorResponse.from(indicator, effectiveThresholds(farmId)))
                .toList();
    }

    private Map<String, ThresholdConfig> effectiveThresholds(Long farmId) {
        Map<String, ThresholdConfig> thresholds = new HashMap<>();
        for (String metric : List.of("BHI", "BSI", "WFR")) {
            thresholdRepository.findEffective(farmId, metric).ifPresent(value -> thresholds.put(metric, value));
        }
        return thresholds;
    }
}
