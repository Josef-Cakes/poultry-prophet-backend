package com.poultryprophet.analytics;

import com.poultryprophet.alert.AlertService;
import com.poultryprophet.batch.Batch;
import com.poultryprophet.batch.BatchRepository;
import com.poultryprophet.config.AnalyticsProperties;
import com.poultryprophet.realtime.RealtimeNotificationService;
import com.poultryprophet.record.DailyRecord;
import com.poultryprophet.record.DailyRecordRepository;
import com.poultryprophet.record.event.RecordCreatedEvent;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * SDD 2.1 IndicatorJobWorker. Replaces the Node.js BullMQ worker: consumes
 * {@link RecordCreatedEvent} after the originating transaction commits, recomputes the
 * batch's indicators off the request thread, persists them, pushes a real-time update, and
 * hands the indicator to the alert engine.
 */
@Component
public class IndicatorJobWorker {

    private final DailyRecordRepository recordRepository;
    private final BatchRepository batchRepository;
    private final IndicatorRepository indicatorRepository;
    private final AnalyticsService analyticsService;
    private final AlertService alertService;
    private final RealtimeNotificationService realtime;
    private final int windowDays;

    public IndicatorJobWorker(DailyRecordRepository recordRepository,
                              BatchRepository batchRepository,
                              IndicatorRepository indicatorRepository,
                              AnalyticsService analyticsService,
                              AlertService alertService,
                              RealtimeNotificationService realtime,
                              AnalyticsProperties props) {
        this.recordRepository = recordRepository;
        this.batchRepository = batchRepository;
        this.indicatorRepository = indicatorRepository;
        this.analyticsService = analyticsService;
        this.alertService = alertService;
        this.realtime = realtime;
        this.windowDays = props.getWindowDays();
    }

    @Async("analyticsExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onRecordCreated(RecordCreatedEvent event) {
        List<Indicator> indicators = recompute(event.batchId());
        if (indicators.isEmpty()) {
            return;
        }
        Indicator latest = indicators.stream()
                .max((left, right) -> left.getRecord().getRecordDate().compareTo(right.getRecord().getRecordDate()))
                .orElse(null);
        if (latest != null) {
            alertService.evaluate(latest);
            realtime.publishIndicatorUpdated(latest);
        }
    }

    /**
     * Recomputes by observation date, not event arrival time. Rebuilding the full small MVP
     * series is deliberate: a backdated edit can change every later rolling-window result.
     */
    private List<Indicator> recompute(Long batchId) {
        Batch batch = batchRepository.findById(batchId).orElse(null);
        if (batch == null) {
            return List.of();
        }
        List<DailyRecord> records = recordRepository.findByBatchIdOrderByRecordDateAsc(batchId);
        List<Indicator> changed = new ArrayList<>();
        for (int index = 0; index < records.size(); index++) {
            DailyRecord record = records.get(index);
            int first = Math.max(0, index - windowDays + 1);
            List<DailyRecord> recentDesc = new ArrayList<>(records.subList(first, index + 1));
            Collections.reverse(recentDesc);
            IndicatorResult result = analyticsService.compute(recentDesc, batch);
            if (result == null) {
                continue;
            }
            Indicator indicator = indicatorRepository.findByRecordId(record.getId())
                    .orElseGet(Indicator::new);
            indicator.setRecord(record);
            indicator.setBatch(batch);
            indicator.setBhi(result.bhi());
            indicator.setBsi(result.bsi());
            indicator.setWfr(result.wfr());
            indicator.setReadinessScore(result.readinessScore());
            indicator.setTemperatureC(record.getTemperatureC());
            indicator.setMortalityCount(record.getMortalityCount());
            indicator.setFeedIntakeG(record.getFeedIntakeG());
            indicator.setWaterIntakeMl(record.getWaterIntakeMl());
            indicator.setTemperatureScore(result.temperatureScore());
            indicator.setMortalityScore(result.mortalityScore());
            indicator.setFeedScore(result.feedScore());
            indicator.setWaterScore(result.waterScore());
            indicator.setTemperatureContribution(result.temperatureContribution());
            indicator.setMortalityContribution(result.mortalityContribution());
            indicator.setFeedContribution(result.feedContribution());
            indicator.setWaterContribution(result.waterContribution());
            indicator.setTemperatureQuality(record.getTemperatureQuality());
            indicator.setFeedQuality(record.getFeedQuality());
            indicator.setWaterQuality(record.getWaterQuality());
            indicator.setFormulaVersion(result.formulaVersion());
            indicator.setSufficientData(result.sufficientData());
            indicator.setMissingDataWarning(result.missingDataWarning());
            indicator.setComputedAt(Instant.now());
            changed.add(indicatorRepository.save(indicator));
        }
        return changed;
    }
}
