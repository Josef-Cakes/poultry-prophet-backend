package com.poultryprophet.record;

import com.poultryprophet.batch.Batch;
import com.poultryprophet.batch.BatchService;
import com.poultryprophet.common.BadRequestException;
import com.poultryprophet.common.DateValidationService;
import com.poultryprophet.common.QueryLimits;
import com.poultryprophet.config.LegacyDataProperties;
import com.poultryprophet.event.MortalityAccountingService;
import com.poultryprophet.record.dto.CreateRecordRequest;
import com.poultryprophet.record.dto.DailyRecordResponse;
import com.poultryprophet.record.event.RecordCreatedEvent;
import com.poultryprophet.user.User;
import com.poultryprophet.user.UserRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * SDD 1.1: record creation with duplicate-day detection and idempotent upsert. Emits a
 * {@link RecordCreatedEvent} so analytics can recompute indicators.
 */
@Service
public class DailyRecordService {

    private final DailyRecordRepository recordRepository;
    private final BatchService batchService;
    private final MortalityAccountingService mortalityAccountingService;
    private final UserRepository userRepository;
    private final ApplicationEventPublisher events;
    private final DateValidationService dateValidation;
    private final LegacyDataProperties legacyDataProperties;

    public DailyRecordService(DailyRecordRepository recordRepository,
                              BatchService batchService,
                              MortalityAccountingService mortalityAccountingService,
                              UserRepository userRepository,
                              ApplicationEventPublisher events,
                              DateValidationService dateValidation,
                              LegacyDataProperties legacyDataProperties) {
        this.recordRepository = recordRepository;
        this.batchService = batchService;
        this.mortalityAccountingService = mortalityAccountingService;
        this.userRepository = userRepository;
        this.events = events;
        this.dateValidation = dateValidation;
        this.legacyDataProperties = legacyDataProperties;
    }

    @Transactional
    public DailyRecordResponse record(Long batchId, Long farmId, CreateRecordRequest req, Long handlerId) {
        LocalDate date = dateValidation.resolve(req.recordDate());
        DailyRecord saved = upsert(batchId, farmId, handlerId, date,
                req.temperatureC(), req.mortalityCount(), req.feedIntakeG(), req.waterIntakeMl(),
                req.behaviorNotes(), req.temperatureQuality(), req.feedQuality(), req.waterQuality(),
                Instant.now(), SyncStatus.SYNCED);
        return DailyRecordResponse.from(saved);
    }

    /**
     * Idempotent upsert keyed on (batch, recordDate). Loads the batch inside this
     * transaction so canonical health-death totals are mirrored into the daily record, then
     * publishes a record-created event. Legacy mortality reconciliation is explicitly opt-in.
     */
    @Transactional
    public DailyRecord upsert(Long batchId, Long farmId, Long handlerId, LocalDate date,
                              double temperatureC, Integer mortalityCount, Double feedIntakeG,
                              Double waterIntakeMl, String behaviorNotes,
                              Instant updatedAt, SyncStatus syncStatus) {
        return upsert(batchId, farmId, handlerId, date, temperatureC, mortalityCount, feedIntakeG,
                waterIntakeMl, behaviorNotes, ObservationQuality.UNKNOWN, ObservationQuality.UNKNOWN,
                ObservationQuality.UNKNOWN, updatedAt, syncStatus);
    }

    @Transactional
    public DailyRecord upsert(Long batchId, Long farmId, Long handlerId, LocalDate date,
                              double temperatureC, Integer mortalityCount, Double feedIntakeG,
                              Double waterIntakeMl, String behaviorNotes,
                              ObservationQuality temperatureQuality, ObservationQuality feedQuality,
                              ObservationQuality waterQuality, Instant updatedAt, SyncStatus syncStatus) {
        Batch batch = batchService.requireBatch(batchId, farmId);
        LocalDate selectedDate = dateValidation.resolve(date);
        dateValidation.validate(selectedDate, batch.getStartDate());
        DailyRecord record = recordRepository.findByBatchIdAndRecordDate(batch.getId(), selectedDate)
                .orElseGet(DailyRecord::new);
        int canonicalHealthDeaths = mortalityAccountingService.healthDeathsForDate(batchId, selectedDate);
        int derivedMortality;
        if (legacyDataProperties.isAllowMortalityReconciliation()) {
            derivedMortality = mortalityAccountingService.reconcileLegacyMortality(
                    batchId, farmId, handlerId, selectedDate, mortalityCount);
            batch = batchService.requireBatch(batchId, farmId);
        } else {
            if (mortalityCount != null && mortalityCount != canonicalHealthDeaths) {
                throw new BadRequestException(
                        "Legacy mortalityCount is not accepted automatically; log a typed HEALTH_DEATH event or run a reviewed migration");
            }
            // Keep an existing ambiguous legacy value unchanged until it is explicitly reviewed.
            derivedMortality = canonicalHealthDeaths == 0 && record.getId() != null
                    ? record.getMortalityCount()
                    : canonicalHealthDeaths;
        }

        User handler = userRepository.getReferenceById(handlerId);
        record.setBatch(batch);
        record.setHandler(handler);
        record.setRecordDate(selectedDate);
        record.setTemperatureC(temperatureC);
        record.setMortalityCount(derivedMortality);
        record.setFeedIntakeG(feedIntakeG);
        record.setWaterIntakeMl(waterIntakeMl);
        record.setTemperatureQuality(temperatureQuality);
        record.setFeedQuality(feedQuality);
        record.setWaterQuality(waterQuality);
        record.setBehaviorNotes(behaviorNotes);
        record.setSyncStatus(syncStatus);
        record.setUpdatedAt(updatedAt);

        DailyRecord persisted = recordRepository.save(record);
        events.publishEvent(new RecordCreatedEvent(persisted.getId(), batch.getId()));
        return persisted;
    }

    @Transactional(readOnly = true)
    public List<DailyRecordResponse> recent(Long batchId, Long farmId, int limit) {
        batchService.requireBatch(batchId, farmId);
        return recordRepository
                .findByBatchIdOrderByRecordDateDesc(batchId, PageRequest.of(0, QueryLimits.clamp(limit)))
                .stream()
                .map(DailyRecordResponse::from)
                .toList();
    }
}
