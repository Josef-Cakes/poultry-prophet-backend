package com.poultryprophet.alert;

import com.poultryprophet.alert.dto.AlertResponse;
import com.poultryprophet.analytics.Indicator;
import com.poultryprophet.analytics.ThresholdConfig;
import com.poultryprophet.analytics.ThresholdConfigRepository;
import com.poultryprophet.batch.Batch;
import com.poultryprophet.batch.BatchRepository;
import com.poultryprophet.batch.BatchStatus;
import com.poultryprophet.batch.BatchService;
import com.poultryprophet.common.BadRequestException;
import com.poultryprophet.common.NotFoundException;
import com.poultryprophet.common.PageResponse;
import com.poultryprophet.common.QueryLimits;
import com.poultryprophet.event.BatchEvent;
import com.poultryprophet.event.BatchEventRepository;
import com.poultryprophet.event.EventType;
import com.poultryprophet.event.MortalityRecordedEvent;
import com.poultryprophet.realtime.RealtimeNotificationService;
import com.poultryprophet.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.ArrayList;

/**
 * SDD 2.3: evaluates computed indicators against thresholds, classifies severity, persists
 * alerts (de-duplicated against active ones), and pushes a real-time {@code alertCreated}.
 */
@Service
public class AlertService {

    private static final Logger log = LoggerFactory.getLogger(AlertService.class);

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
        createDeathAlert(event, true);
    }

    /** Repairs death events created before accidental deaths produced direct alerts. */
    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void backfillMissingDeathAlerts() {
        int repaired = 0;
        for (BatchEvent source : batchEventRepository.findDeathEventsMissingAlerts(
                List.of(EventType.HEALTH_DEATH, EventType.ACCIDENTAL_DEATH))) {
            if (alertRepository.existsBySourceEvent_Id(source.getId())) {
                continue;
            }
            Batch batch = batchRepository.findById(source.getBatchId()).orElse(null);
            if (batch == null || batch.getStatus() == BatchStatus.ARCHIVED) {
                continue;
            }
            createDeathAlert(new MortalityRecordedEvent(
                    source.getId(), source.getBatchId(), source.getHandlerId(), source.getEventDate(),
                    source.getEventType(), source.getAffectedCount(),
                    source.getPopulationAfter() == null ? batch.getCurrentPopulation() : source.getPopulationAfter(),
                    source.getTitle()), false);
            repaired++;
        }
        if (repaired > 0) {
            log.info("Repaired {} missing death alert(s)", repaired);
        }
    }

    private void createDeathAlert(MortalityRecordedEvent event, boolean publishRealtime) {
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
        String typeLabel = event.eventType() == EventType.ACCIDENTAL_DEATH
                ? "accidental" : "health-related";
        String message = String.format("%s logged %d %s in %s on %s. Cause: %s.",
                handlerName, event.affectedCount(), typeLabel + " " + deaths, batchName,
                event.eventDate(), cause);

        Alert alert = new Alert();
        alert.setBatch(batch);
        alert.setSourceEvent(batchEventRepository.getReferenceById(event.eventId()));
        alert.setIndicatorType(event.eventType().name());
        alert.setSeverity(event.remainingPopulation() == 0 ? Severity.CRITICAL : Severity.WARNING);
        alert.setMessage(message);
        alert.setBatchName(batchName);
        alert.setHandlerName(handlerName);
        alert.setDeathCount(event.affectedCount());
        alert.setCause(cause);
        alert.setOccurrenceDate(event.eventDate());
        Alert saved = alertRepository.save(alert);
        if (publishRealtime) {
            realtime.publishAlertCreated(saved);
        }
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

    @Transactional(readOnly = true)
    public PageResponse<AlertResponse> pageForFarm(Long farmId, boolean activeOnly, Severity severity,
                                                    Long batchId, String sort, int page, int size) {
        int safePage = Math.max(0, page);
        int safeSize = Math.max(1, Math.min(size, 50));
        String requestedSort = sort == null || sort.isBlank() ? "priority" : sort.trim().toLowerCase(java.util.Locale.ROOT);
        if (!requestedSort.equals("priority") && !requestedSort.equals("newest")) {
            throw new BadRequestException("Alert sort must be priority or newest");
        }

        Specification<Alert> specification = alertSpecification(farmId, activeOnly, severity, batchId);
        PageRequest pageRequest;
        if (requestedSort.equals("priority")) {
            /*
             * JpaSort.unsafe is intended for string-based @Query sorting. This
             * repository call uses Criteria queries, where Spring Data resolves
             * every sort property as an Alert field and rejects a raw CASE
             * expression. Put the CASE expression in the Criteria query itself,
             * and do not apply it to the count query used by pagination.
             */
            specification = specification.and((root, query, cb) -> {
                if (!isCountQuery(query)) {
                    var priority = cb.<Integer>selectCase()
                            .when(cb.equal(root.get("severity"), Severity.CRITICAL), 0)
                            .when(cb.equal(root.get("severity"), Severity.WARNING), 1)
                            .otherwise(2);
                    query.orderBy(cb.asc(priority), cb.desc(root.get("createdAt")), cb.desc(root.get("id")));
                }
                return cb.conjunction();
            });
            pageRequest = PageRequest.of(safePage, safeSize, Sort.unsorted());
        } else {
            pageRequest = PageRequest.of(safePage, safeSize,
                    Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
        }

        Page<Alert> result = alertRepository.findAll(specification, pageRequest);
        return PageResponse.from(result, AlertResponse::from);
    }

    @Transactional(readOnly = true)
    public AlertSummaryResponse summaryForFarm(Long farmId) {
        long active = countByFarm(farmId, null);
        Map<Severity, Long> counts = new LinkedHashMap<>();
        for (Severity value : Severity.values()) counts.put(value, countByFarm(farmId, value));
        return new AlertSummaryResponse(active, counts.getOrDefault(Severity.CRITICAL, 0L),
                counts.getOrDefault(Severity.WARNING, 0L), counts.getOrDefault(Severity.INFO, 0L));
    }

    private long countByFarm(Long farmId, Severity severity) {
        return alertRepository.count(alertSpecification(farmId, true, severity, null));
    }

    private Specification<Alert> alertSpecification(Long farmId, boolean activeOnly, Severity severity,
                                                      Long batchId) {
        return (root, query, cb) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.join("batch").get("farmId"), farmId));
            jakarta.persistence.criteria.Expression<String> indicatorType = root.get("indicatorType");
            predicates.add(cb.or(
                    cb.isNull(indicatorType),
                    cb.not(indicatorType.in("BHI", "BSI", "WFR", "CRS"))));
            if (activeOnly) predicates.add(cb.isNull(root.get("acknowledgedAt")));
            if (severity != null) predicates.add(cb.equal(root.get("severity"), severity));
            if (batchId != null) predicates.add(cb.equal(root.get("batch").get("id"), batchId));
            return cb.and(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
    }

    private boolean isCountQuery(jakarta.persistence.criteria.CriteriaQuery<?> query) {
        Class<?> resultType = query.getResultType();
        return Long.class.equals(resultType) || long.class.equals(resultType);
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
