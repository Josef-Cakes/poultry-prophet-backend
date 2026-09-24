package com.poultryprophet.alert;

import com.poultryprophet.alert.dto.AlertResponse;
import com.poultryprophet.analytics.Indicator;
import com.poultryprophet.analytics.ThresholdConfig;
import com.poultryprophet.analytics.ThresholdConfigRepository;
import com.poultryprophet.batch.Batch;
import com.poultryprophet.batch.BatchRepository;
import com.poultryprophet.batch.BatchService;
import com.poultryprophet.common.NotFoundException;
import com.poultryprophet.common.QueryLimits;
import com.poultryprophet.event.BatchEventRepository;
import com.poultryprophet.event.MortalityRecordedEvent;
import com.poultryprophet.realtime.RealtimeNotificationService;
import com.poultryprophet.user.UserRepository;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * SDD 2.3: evaluates computed indicators against thresholds, classifies severity, persists
 * alerts (de-duplicated against active ones), and pushes a real-time {@code alertCreated}.
 */
@Service
public class AlertService {

    private final AlertRepository alertRepository;
    private final SeverityClassifier severityClassifier;
    private final ThresholdConfigRepository thresholdRepository;
    private final RealtimeNotificationService realtime;
    private final UserRepository userRepository;
    private final BatchService batchService;
    private final BatchRepository batchRepository;
    private final BatchEventRepository batchEventRepository;

    public AlertService(AlertRepository alertRepository,
                        SeverityClassifier severityClassifier,
                        ThresholdConfigRepository thresholdRepository,
                        RealtimeNotificationService realtime,
                        UserRepository userRepository,
                        BatchService batchService,
                        BatchRepository batchRepository,
                        BatchEventRepository batchEventRepository) {
        this.alertRepository = alertRepository;
        this.severityClassifier = severityClassifier;
        this.thresholdRepository = thresholdRepository;
        this.realtime = realtime;
        this.userRepository = userRepository;
        this.batchService = batchService;
        this.batchRepository = batchRepository;
        this.batchEventRepository = batchEventRepository;
    }

    /** Invoked by the analytics worker within its transaction after an indicator is saved. */
    public void evaluate(Indicator indicator) {
        Batch batch = indicator.getBatch();
        Long farmId = batch.getFarmId();

        if (indicator.getBhi() == null) {
            raise(batch, indicator, "BHI", Severity.WARNING,
                    indicator.getMissingDataWarning() == null
                            ? "BHI is unavailable because there is insufficient baseline data."
                            : indicator.getMissingDataWarning());
        } else {
            evaluateMetric(indicator, batch, "BHI", indicator.getBhi(), farmId);
        }

        if (indicator.getBsi() != null) {
            evaluateMetric(indicator, batch, "BSI", indicator.getBsi(), farmId);
        }

        if (indicator.getWfr() == null) {
            raise(batch, indicator, "WFR", Severity.WARNING,
                    "Zero feed intake recorded — water-to-feed ratio could not be computed.");
        } else {
            evaluateMetric(indicator, batch, "WFR", indicator.getWfr(), farmId);
        }
    }

    private void evaluateMetric(Indicator indicator, Batch batch, String type, Double value, Long farmId) {
        if (value == null) {
            return;
        }
        thresholdRepository.findEffective(farmId, type).ifPresent(threshold ->
                severityClassifier.classify(value, threshold).ifPresent(severity ->
                        raise(batch, indicator, type, severity, describe(type, value, threshold))));
    }

    private String describe(String type, double value, ThresholdConfig threshold) {
        return String.format("%s %.1f is outside the expected range %.1f–%.1f.",
                type, value, threshold.getMinValue(), threshold.getMaxValue());
    }

    private void raise(Batch batch, Indicator indicator, String type, Severity severity, String message) {
        if (alertRepository.existsByBatchIdAndIndicatorTypeAndAcknowledgedAtIsNull(batch.getId(), type)) {
            return; // an unacknowledged alert of this type already stands
        }
        Alert alert = new Alert();
        alert.setBatch(batch);
        alert.setIndicator(indicator);
        alert.setIndicatorType(type);
        alert.setSeverity(severity);
        alert.setMessage(message);
        Alert saved = alertRepository.save(alert);
        realtime.publishAlertCreated(saved);
    }

    /** Creates the guaranteed direct alert in the mortality transaction itself. */
    @EventListener
    @Transactional
    public void onMortalityRecorded(MortalityRecordedEvent event) {
        if (alertRepository.existsBySourceEvent_Id(event.eventId())) {
            return;
        }

        Batch batch = batchRepository.getReferenceById(event.batchId());
        String batchName = batch.getName();
        String handlerName = userRepository.findById(event.handlerId())
                .map(user -> user.getFullName()).orElse("Unknown handler");
        String cause = event.cause() == null || event.cause().isBlank()
                ? "Unknown" : event.cause();
        String deaths = event.affectedCount() == 1 ? "death" : "deaths";
        String message = String.format("%s logged %d %s in %s on %s. Cause: %s.",
                handlerName, event.affectedCount(), deaths, batchName,
                event.eventDate(), cause);

        Alert alert = new Alert();
        alert.setBatch(batch);
        alert.setSourceEvent(batchEventRepository.getReferenceById(event.eventId()));
        alert.setIndicatorType("HEALTH_DEATH");
        alert.setSeverity(event.remainingPopulation() == 0 ? Severity.CRITICAL : Severity.WARNING);
        alert.setMessage(message);
        alert.setBatchName(batchName);
        alert.setHandlerName(handlerName);
        alert.setDeathCount(event.affectedCount());
        alert.setCause(cause);
        alert.setOccurrenceDate(event.eventDate());
        Alert saved = alertRepository.save(alert);
        realtime.publishAlertCreated(saved);
    }

    @Transactional(readOnly = true)
    public List<AlertResponse> list(Long batchId, Long farmId, boolean activeOnly, int limit) {
        batchService.requireBatch(batchId, farmId);
        PageRequest page = PageRequest.of(0, QueryLimits.clamp(limit));
        List<Alert> alerts = activeOnly
                ? alertRepository.findByBatchIdAndAcknowledgedAtIsNullOrderByCreatedAtDesc(batchId, page)
                : alertRepository.findByBatchIdOrderByCreatedAtDesc(batchId, page);
        return alerts.stream().filter(alert -> !isLegacyScoreAlert(alert)).map(AlertResponse::from).toList();
    }

    /** SDD 2.3: farm-wide feed across every batch, backing the notifications centre. */
    @Transactional(readOnly = true)
    public List<AlertResponse> listForFarm(Long farmId, boolean activeOnly, int limit) {
        PageRequest page = PageRequest.of(0, QueryLimits.clamp(limit));
        List<Alert> alerts = activeOnly
                ? alertRepository.findByBatch_FarmIdAndAcknowledgedAtIsNullOrderByCreatedAtDesc(farmId, page)
                : alertRepository.findByBatch_FarmIdOrderByCreatedAtDesc(farmId, page);
        return alerts.stream().filter(alert -> !isLegacyScoreAlert(alert)).map(AlertResponse::from).toList();
    }

    @Transactional
    public AlertResponse acknowledge(Long alertId, Long farmId, Long userId, String note) {
        Alert alert = alertRepository.findById(alertId)
                .orElseThrow(() -> new NotFoundException("Alert " + alertId + " not found"));
        if (!alert.getBatch().getFarmId().equals(farmId)) {
            throw new NotFoundException("Alert " + alertId + " not found");
        }
        alert.setAcknowledgedBy(userRepository.getReferenceById(userId));
        alert.setAcknowledgedAt(Instant.now());
        alert.setAcknowledgmentNote(note);
        return AlertResponse.from(alertRepository.save(alert));
    }

    private boolean isLegacyScoreAlert(Alert alert) {
        if (alert.getIndicatorType() == null) return false;
        return switch (alert.getIndicatorType()) {
            case "BHI", "BSI", "WFR", "CRS" -> true;
            default -> false;
        };
    }
}
