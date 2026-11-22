package com.poultryprophet.batch;

import com.poultryprophet.batch.dto.BatchResponse;
import com.poultryprophet.common.ConflictException;
import com.poultryprophet.event.BatchEvent;
import com.poultryprophet.event.BatchEventRepository;
import com.poultryprophet.population.PopulationProjection;
import com.poultryprophet.population.PopulationProjectionService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Comparator;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Repairs only the derived current-population field when the append-only event ledger is valid.
 * It never creates a synthetic death or correction event, so the historical record remains the
 * source of truth and an offline record can be retried without double-counting.
 */
@Service
public class BatchPopulationReconciliationService {

    private final BatchService batchService;
    private final BatchRepository batchRepository;
    private final BatchEventRepository eventRepository;
    private final BatchLifecycleAuditRepository auditRepository;
    private final PopulationProjectionService populationProjectionService;
    private final ZoneId farmZone;

    public BatchPopulationReconciliationService(BatchService batchService,
                                                BatchRepository batchRepository,
                                                BatchEventRepository eventRepository,
                                                BatchLifecycleAuditRepository auditRepository,
                                                PopulationProjectionService populationProjectionService,
                                                @Value("${app.time-zone:Asia/Manila}") String timeZone) {
        this.batchService = batchService;
        this.batchRepository = batchRepository;
        this.eventRepository = eventRepository;
        this.auditRepository = auditRepository;
        this.populationProjectionService = populationProjectionService;
        this.farmZone = ZoneId.of(timeZone == null ? "Asia/Manila" : timeZone);
    }

    @Transactional
    public BatchResponse reconcile(Long batchId, Long farmId, Long managerId) {
        return reconcile(batchId, farmId, managerId, null);
    }

    @Transactional(readOnly = true)
    public PopulationDiagnostics diagnostics(Long batchId, Long farmId) {
        Batch batch = batchService.requireBatch(batchId, farmId);
        LocalDate today = LocalDate.now(farmZone);
        return diagnose(batch, loadEvents(batch, today), today);
    }

    @Transactional
    public BatchResponse reconcile(Long batchId, Long farmId, Long managerId, String expectedRevision) {
        Batch batch = batchService.requireBatchForUpdate(batchId, farmId);
        batchService.ensureWritable(batch);

        LocalDate today = LocalDate.now(farmZone);
        List<BatchEvent> events = loadEvents(batch, today);
        if (expectedRevision != null && !expectedRevision.equals(revision(batch, events, today))) {
            throw new ConflictException("STALE_DIAGNOSTICS: population records changed; refresh and review them again.");
        }
        PopulationProjection projection = populationProjectionService.projectLedger(
                batch, events, today, farmZone);
        Integer calculated = projection.validPopulation();
        if (projection.reconciliationRequired() || calculated == null) {
            throw new ConflictException("Population history cannot be reconciled safely: "
                    + (projection.reconciliationMessage() == null
                    ? "review the population events first."
                    : projection.reconciliationMessage()));
        }

        int previous = batch.getCurrentPopulation();
        if (previous != calculated) {
            batch.setCurrentPopulation(calculated);
            batchRepository.save(batch);

            BatchLifecycleAudit audit = new BatchLifecycleAudit();
            audit.setFarmId(farmId);
            audit.setBatchId(batch.getId());
            audit.setBatchNameSnapshot(batch.getName());
            audit.setAction("POPULATION_RECONCILE");
            audit.setReason("Reconciled stored count from the population event ledger");
            audit.setPerformedByUserId(managerId);
            audit.setPerformedAt(Instant.now());
            audit.setMetadataJson("{\"previousCount\":" + previous
                    + ",\"calculatedCount\":" + calculated
                    + ",\"sourceEventCount\":" + events.size() + "}");
            auditRepository.save(audit);
        }

        return batchService.responseFor(batch);
    }

    private List<BatchEvent> loadEvents(Batch batch, LocalDate today) {
        return eventRepository.findByBatchIdAndEventDateBetweenOrderByEventDateAscCreatedAtAsc(
                batch.getId(), batch.getHatchDate(), today);
    }

    private PopulationDiagnostics diagnose(Batch batch, List<BatchEvent> events, LocalDate today) {
        PopulationProjection ledger = populationProjectionService.projectLedger(batch, events, today, farmZone);
        boolean valid = !ledger.reconciliationRequired() && ledger.validPopulation() != null;
        boolean mismatch = valid && ledger.validPopulation() != batch.getCurrentPopulation();
        String status = !valid ? "INVALID_LEDGER" : mismatch ? "STORED_MISMATCH" : "VALID";
        return new PopulationDiagnostics(today, status, batch.getInitialPopulation(), batch.getCurrentPopulation(),
                ledger.calculatedPopulation(), valid, mismatch && batch.getStatus() != BatchStatus.ARCHIVED,
                ledger.issueCode(), ledger.reconciliationMessage(), ledger.firstInvalidEventId(),
                ledger.firstInvalidEventDate(), events.size(), revision(batch, events, today));
    }

    private String revision(Batch batch, List<BatchEvent> events, LocalDate today) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            update(digest, batch.getFarmId(), batch.getId(), batch.getHatchDate(), today,
                    batch.getInitialPopulation(), batch.getCurrentPopulation(), batch.getStatus());
            events.stream().sorted(Comparator
                    .comparing(BatchEvent::getEventDate, Comparator.nullsLast(Comparator.naturalOrder()))
                    .thenComparing(BatchEvent::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder()))
                    .thenComparing(BatchEvent::getId, Comparator.nullsLast(Comparator.naturalOrder())))
                    .forEach(event -> update(digest, event.getId(), event.getEventDate(), event.getCreatedAt(),
                            event.getEventType(), event.getAffectedCount(), event.getPopulationDelta()));
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    private void update(MessageDigest digest, Object... values) {
        for (Object value : values) {
            digest.update(String.valueOf(value).getBytes(StandardCharsets.UTF_8));
            digest.update((byte) 0);
        }
    }

    public record PopulationDiagnostics(LocalDate asOfDate, String status, int initialPopulation,
            int storedPopulation, long calculatedPopulation, boolean ledgerValid, boolean canRepairStoredCount,
            String issueCode, String reason, Long firstInvalidEventId, LocalDate firstInvalidEventDate,
            int sourceEventCount, String revision) {}
}
