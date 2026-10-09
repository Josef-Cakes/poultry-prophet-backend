package com.poultryprophet.batch;

import com.poultryprophet.batch.dto.ArchiveBatchRequest;
import com.poultryprophet.batch.dto.BatchResponse;
import com.poultryprophet.batch.dto.BatchRetirementImpactResponse;
import com.poultryprophet.batch.dto.DeleteBatchRequest;
import com.poultryprophet.common.BadRequestException;
import com.poultryprophet.common.ConflictException;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Owns the destructive/lifecycle boundary for batches. Archiving is the normal reversible
 * action; deletion is deliberately conservative and only applies to an empty archived batch.
 */
@Service
public class BatchRetirementService {
    private final BatchRepository batchRepository;
    private final BatchHandlerAssignmentRepository assignmentRepository;
    private final BatchLifecycleAuditRepository auditRepository;
    private final BatchService batchService;
    private final EntityManager entityManager;

    public BatchRetirementService(BatchRepository batchRepository,
                                  BatchHandlerAssignmentRepository assignmentRepository,
                                  BatchLifecycleAuditRepository auditRepository,
                                  BatchService batchService,
                                  EntityManager entityManager) {
        this.batchRepository = batchRepository;
        this.assignmentRepository = assignmentRepository;
        this.auditRepository = auditRepository;
        this.batchService = batchService;
        this.entityManager = entityManager;
    }

    @Transactional(readOnly = true)
    public BatchRetirementImpactResponse impact(Long batchId, Long farmId) {
        Batch batch = batchService.requireBatch(batchId, farmId);
        return impactFor(batch);
    }

    @Transactional
    public BatchResponse archive(Long batchId, Long farmId, Long userId, ArchiveBatchRequest request) {
        Batch batch = batchService.requireBatchForUpdate(batchId, farmId);
        if (batch.getStatus() == BatchStatus.ARCHIVED) return batchService.responseFor(batch);

        // Archiving is a reversible lifecycle update. Keep its preflight small and
        // deterministic: unrelated historical tables must not prevent a manager from
        // retiring a batch. The full record inventory remains available through the
        // retirement-impact endpoint and is still required for permanent deletion.
        long openTaskCount = openTaskCount(batch.getId());
        if (openTaskCount > 0) {
            throw new ConflictException("BATCH_HAS_OPEN_TASKS: complete or cancel "
                    + openTaskCount + " open batch task(s) before archiving it");
        }

        BatchStatus previous = batch.getStatus();
        Instant archivedAt = Instant.now();
        String archiveReason = clean(request == null ? null : request.reason());
        int updated = batchRepository.updateArchiveLifecycle(batchId, farmId, previous, BatchStatus.ARCHIVED,
                archivedAt, userId, archiveReason);
        if (updated == 0) {
            // A concurrent retry may have archived it after the lock was released. Return the
            // already archived state rather than writing a second transition.
            Batch current = batchService.requireBatch(batchId, farmId);
            if (current.getStatus() == BatchStatus.ARCHIVED) return batchService.responseFor(current);
            throw new ConflictException("The batch changed before it could be archived. Refresh and try again.");
        }
        Batch saved = batchService.requireBatch(batchId, farmId);
        audit(saved, farmId, "ARCHIVE", previous, BatchStatus.ARCHIVED, saved.getArchiveReason(), userId, null);
        return batchService.responseFor(saved);
    }

    @Transactional
    public BatchResponse restore(Long batchId, Long farmId, Long userId) {
        Batch batch = batchService.requireBatchForUpdate(batchId, farmId);
        if (batch.getStatus() != BatchStatus.ARCHIVED) return batchService.responseFor(batch);
        BatchStatus previous = batch.getStatus();
        BatchStatus restored = batch.getPreArchiveStatus() == null ? BatchStatus.ACTIVE : batch.getPreArchiveStatus();
        int updated = batchRepository.updateRestoreLifecycle(batchId, farmId, BatchStatus.ARCHIVED, restored);
        if (updated == 0) {
            Batch current = batchService.requireBatch(batchId, farmId);
            if (current.getStatus() != BatchStatus.ARCHIVED) return batchService.responseFor(current);
            throw new ConflictException("The batch changed before it could be restored. Refresh and try again.");
        }
        Batch saved = batchService.requireBatch(batchId, farmId);
        audit(saved, farmId, "RESTORE", previous, restored, null, userId, null);
        return batchService.responseFor(saved);
    }

    @Transactional
    public void delete(Long batchId, Long farmId, Long userId, DeleteBatchRequest request) {
        Batch batch = batchService.requireBatchForUpdate(batchId, farmId);
        if (batch.getStatus() != BatchStatus.ARCHIVED) {
            throw new ConflictException("BATCH_MUST_BE_ARCHIVED: archive the batch before deleting it");
        }
        String confirmation = request == null ? null : request.confirmationName();
        if (confirmation == null || !confirmation.trim().equals(batch.getName())) {
            throw new BadRequestException("Type the exact batch name to permanently delete it");
        }
        BatchRetirementImpactResponse impact = impactFor(batch);
        if (!impact.canDelete()) {
            throw new ConflictException("BATCH_HAS_RECORDS: this batch contains records and must remain archived");
        }
        audit(batch, farmId, "DELETE", BatchStatus.ARCHIVED, null,
                clean(request == null ? null : request.reason()), userId,
                "deletedEmptyBatch=true");
        assignmentRepository.deleteAll(assignmentRepository.findByBatchId(batchId));
        batchRepository.delete(batch);
    }

    private BatchRetirementImpactResponse impactFor(Batch batch) {
        Map<String, Long> counts = new LinkedHashMap<>();
        counts.put("events", count("select count(*) from batch_event where batch_id = :batchId", batch.getId()));
        counts.put("dailyRecords", count("select count(*) from daily_record where batch_id = :batchId", batch.getId()));
        counts.put("indicators", count("select count(*) from indicator where batch_id = :batchId", batch.getId()));
        counts.put("alerts", count("select count(*) from alert where batch_id = :batchId", batch.getId()));
        counts.put("productRecords", count("select count(*) from farm_input_log where batch_id = :batchId", batch.getId()));
        counts.put("inventoryMovements", count("select count(*) from inventory_movement where batch_id = :batchId", batch.getId()));
        counts.put("financeTransactions", count("select count(*) from farm_financial_transaction where batch_id = :batchId", batch.getId()));
        counts.put("tasks", count("select count(*) from handler_task where batch_id = :batchId", batch.getId()));
        counts.put("birds", count("select count(*) from bird where batch_id = :batchId", batch.getId()));
        counts.put("selectionRecords", count("select count(*) from selection_record where batch_id = :batchId", batch.getId()));
        counts.put("birdScores", count("select count(*) from bird_score where batch_id = :batchId", batch.getId()));
        counts.put("rangingRecords", count("select count(*) from ranging_record r join bird b on b.id = r.bird_id where b.batch_id = :batchId", batch.getId()));
        counts.put("selectionSessions", count("select count(*) from batch_selection_session where batch_id = :batchId", batch.getId()));
        counts.put("selectionReviews", count("select count(*) from batch_selection_review where batch_id = :batchId", batch.getId()));
        counts.put("reports", count("select count(*) from report where batch_id = :batchId", batch.getId()));
        counts.put("incubationLinks", count("select count(*) from incubation_cycle where created_batch_id = :batchId", batch.getId()));

        long openTasks = count("select count(*) from handler_task where batch_id = :batchId and status not in ('COMPLETED','CANCELLED')", batch.getId());
        long activeAlerts = count("select count(*) from alert where batch_id = :batchId and acknowledged_at is null", batch.getId());
        long records = counts.values().stream().mapToLong(Long::longValue).sum();
        List<String> blockers = new ArrayList<>();
        if (batch.getStatus() != BatchStatus.ARCHIVED) blockers.add("Archive the batch before permanent deletion.");
        if (openTasks > 0) blockers.add("Complete or cancel " + openTasks + " open batch task(s).");
        if (records > 0) blockers.add("This batch contains farm records and must remain archived.");
        return new BatchRetirementImpactResponse(batch.getId(), batch.getName(), batch.getStatus(),
                openTasks == 0, batch.getStatus() == BatchStatus.ARCHIVED && openTasks == 0 && records == 0,
                openTasks, activeAlerts, counts, blockers);
    }

    private long count(String sql, Long batchId) {
        return ((Number) entityManager.createNativeQuery(sql).setParameter("batchId", batchId).getSingleResult()).longValue();
    }

    private long openTaskCount(Long batchId) {
        return count("select count(*) from handler_task where batch_id = :batchId "
                + "and status not in ('COMPLETED','CANCELLED')", batchId);
    }

    private void audit(Batch batch, Long farmId, String action, BatchStatus previous, BatchStatus next,
                       String reason, Long userId, String metadata) {
        BatchLifecycleAudit row = new BatchLifecycleAudit();
        row.setFarmId(farmId);
        row.setBatchId(batch.getId());
        row.setBatchNameSnapshot(batch.getName());
        row.setAction(action);
        row.setPreviousStatus(previous);
        row.setNewStatus(next);
        row.setReason(reason);
        row.setPerformedByUserId(userId);
        row.setPerformedAt(Instant.now());
        row.setMetadataJson(metadata);
        auditRepository.save(row);
    }

    private static String clean(String value) {
        if (value == null) return null;
        String valueTrimmed = value.trim();
        return valueTrimmed.isBlank() ? null : valueTrimmed.substring(0, Math.min(500, valueTrimmed.length()));
    }
}
