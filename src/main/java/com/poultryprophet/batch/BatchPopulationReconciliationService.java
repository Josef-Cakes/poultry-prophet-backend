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
        Batch batch = batchService.requireBatchForUpdate(batchId, farmId);
        batchService.ensureWritable(batch);

        LocalDate today = LocalDate.now(farmZone);
        List<BatchEvent> events = eventRepository
                .findByBatchIdAndEventDateBetweenOrderByEventDateAscCreatedAtAsc(
                        batchId, batch.getStartDate(), today);
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
}
