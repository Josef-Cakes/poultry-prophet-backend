package com.poultryprophet.sync;

import com.poultryprophet.batch.BatchService;
import com.poultryprophet.batch.Batch;
import com.poultryprophet.common.DateValidationService;
import com.poultryprophet.record.DailyRecord;
import com.poultryprophet.record.DailyRecordRepository;
import com.poultryprophet.record.DailyRecordService;
import com.poultryprophet.record.dto.CreateRecordRequest;
import com.poultryprophet.common.ConflictException;
import com.poultryprophet.sync.dto.ConflictType;
import com.poultryprophet.sync.dto.SyncBatchRequest;
import com.poultryprophet.sync.dto.SyncBatchResponse;
import com.poultryprophet.sync.dto.SyncItemRequest;
import com.poultryprophet.sync.dto.SyncItemResult;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * SDD 1.2: accepts a buffer of offline daily records, resolves per-item conflicts, persists
 * the accepted ones, logs each attempt, and returns per-item acknowledgments. Each item is
 * processed independently so a single failure does not roll back the whole batch.
 */
@Service
public class SyncService {

    private final DailyRecordService recordService;
    private final DailyRecordRepository recordRepository;
    private final ConflictResolver conflictResolver;
    private final SyncAttemptRepository syncAttemptRepository;
    private final BatchService batchService;
    private final DateValidationService dateValidation;

    public SyncService(DailyRecordService recordService,
                       DailyRecordRepository recordRepository,
                       ConflictResolver conflictResolver,
                       SyncAttemptRepository syncAttemptRepository,
                       BatchService batchService,
                       DateValidationService dateValidation) {
        this.recordService = recordService;
        this.recordRepository = recordRepository;
        this.conflictResolver = conflictResolver;
        this.syncAttemptRepository = syncAttemptRepository;
        this.batchService = batchService;
        this.dateValidation = dateValidation;
    }

    public SyncBatchResponse sync(SyncBatchRequest request, Long farmId, Long handlerId) {
        org.slf4j.LoggerFactory.getLogger(SyncService.class).warn(
                "Deprecated /api/sync/batch used: records={}; upgrade to UUID-scoped v2 commands. Client clocks cannot authorize overwrites.", request.records().size());
        List<SyncItemResult> results = new ArrayList<>();
        int synced = 0;
        int conflicts = 0;
        int failed = 0;

        for (SyncItemRequest item : request.records()) {
            try {
                // Validates the batch belongs to the syncing handler's farm.
                Batch batch = batchService.requireBatch(item.batchId(), farmId);
                dateValidation.validate(item.recordDate(), batch.getHatchDate());

                DailyRecord existing = recordRepository
                        .findByBatchIdAndRecordDate(item.batchId(), item.recordDate())
                        .orElse(null);
                ConflictType type = conflictResolver.classify(existing, item);

                if (type == ConflictType.NEW) {
                    var saved = recordService.recordOffline(item.batchId(), farmId,
                            new CreateRecordRequest(item.recordDate(), item.temperatureC(), item.mortalityCount(),
                                    item.feedIntakeG(), item.waterIntakeMl(), item.behaviorNotes()), handlerId);
                    log(saved.id(), 200, type.name());
                    results.add(SyncItemResult.synced(item.clientId(), saved.id(), type));
                    synced++;
                } else if (type == ConflictType.IDENTICAL) {
                    // The existing record is authoritative. An identical legacy
                    // retry is a read acknowledgement, not permission to upsert
                    // or rerun mortality/indicator effects after archival.
                    log(existing.getId(), 200, type.name());
                    results.add(SyncItemResult.synced(item.clientId(), existing.getId(), type));
                    synced++;
                } else { // Changed records require review, irrespective of the client clock.
                    log(existing.getId(), 409, type.name());
                    results.add(SyncItemResult.conflict(item.clientId(), existing.getId(), type));
                    conflicts++;
                }
            } catch (ConflictException ex) {
                // A competing writer may have accepted this day after classification.
                // recordOffline rechecks under the batch lock; never fall back to upsert.
                log(null, 409, "CONCURRENT_RECORD");
                results.add(SyncItemResult.conflict(item.clientId(), null, ConflictType.SERVER_NEWER));
                conflicts++;
            } catch (Exception ex) {
                log(null, 400, ex.getClass().getSimpleName());
                results.add(SyncItemResult.failed(item.clientId(), ex.getMessage()));
                failed++;
            }
        }
        return new SyncBatchResponse(request.records().size(), synced, conflicts, failed, results);
    }

    private void log(Long recordId, int httpStatus, String errorCode) {
        syncAttemptRepository.save(new SyncAttempt(recordId, httpStatus, errorCode));
    }
}
