package com.poultryprophet.event;

import com.poultryprophet.batch.Batch;
import com.poultryprophet.batch.BatchRepository;
import com.poultryprophet.batch.BatchService;
import com.poultryprophet.common.BadRequestException;
import com.poultryprophet.common.DateValidationService;
import com.poultryprophet.event.dto.CreateBatchEventRequest;
import com.poultryprophet.record.DailyRecord;
import com.poultryprophet.record.DailyRecordRepository;
import com.poultryprophet.record.event.RecordCreatedEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * The single write path for population-affecting events. Batch events are authoritative; daily
 * records only mirror confirmed health-death totals for analytics and display. Existing legacy
 * MORTALITY rows are intentionally not rewritten by this service.
 */
@Service
public class MortalityAccountingService {

    private final BatchService batchService;
    private final BatchRepository batchRepository;
    private final BatchEventRepository eventRepository;
    private final DailyRecordRepository recordRepository;
    private final ApplicationEventPublisher publisher;
    private final DateValidationService dateValidation;

    public MortalityAccountingService(BatchService batchService,
                                      BatchRepository batchRepository,
                                      BatchEventRepository eventRepository,
                                      DailyRecordRepository recordRepository,
                                      ApplicationEventPublisher publisher,
                                      DateValidationService dateValidation) {
        this.batchService = batchService;
        this.batchRepository = batchRepository;
        this.eventRepository = eventRepository;
        this.recordRepository = recordRepository;
        this.publisher = publisher;
        this.dateValidation = dateValidation;
    }

    @Transactional
    public MortalityAccountingResult record(Long batchId, Long farmId, Long handlerId,
                                            CreateBatchEventRequest request) {
        if (!request.eventType().isPopulationLedgerEvent()) {
            throw new BadRequestException("This event does not affect the population ledger");
        }
        return recordPopulationEvent(batchId, farmId, handlerId, request);
    }

    @Transactional
    public MortalityAccountingResult recordPopulationEvent(Long batchId, Long farmId, Long handlerId,
                                                            CreateBatchEventRequest request) {
        EventType type = normalize(request.eventType());
        LocalDate date = dateValidation.resolve(request.eventDate());
        Batch batch = batchService.requireBatchForUpdate(batchId, farmId);

        if (request.operationId() != null) {
            BatchEvent existing = eventRepository.findByOperationId(request.operationId()).orElse(null);
            if (existing != null) {
                if (!batchId.equals(existing.getBatchId())) {
                    throw new BadRequestException("operationId has already been used for another batch");
                }
                return new MortalityAccountingResult(existing,
                        existing.getPopulationAfter() != null
                                ? existing.getPopulationAfter()
                                : batch.getCurrentPopulation());
            }
        }

        int delta;
        try {
            delta = type.populationDelta(request.affectedCount(), request.populationDelta());
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException(ex.getMessage());
        }
        validate(batch, date, request.affectedCount(), delta, type);

        BatchEvent event = new BatchEvent();
        event.setBatchId(batchId);
        event.setHandlerId(handlerId);
        event.setOperationId(request.operationId() != null ? request.operationId() : UUID.randomUUID());
        event.setEventDate(date);
        event.setEventType(type);
        event.setSeverityLabel(request.severityLabel());
        event.setAffectedCount(request.affectedCount());
        event.setPopulationDelta(delta);
        event.setTitle(request.title());
        event.setDetails(request.details());
        event.setTags(request.tags());

        return persistCanonicalEvent(batch, date, event, delta);
    }

    /**
     * Imports the legacy cumulative Daily Vitals mortality field when explicitly enabled for an
     * owner-reviewed repair. Normal record writes do not call this method in production.
     */
    @Transactional
    public int reconcileLegacyMortality(Long batchId, Long farmId, Long handlerId,
                                        LocalDate date, Integer requestedMortalityCount) {
        Batch batch = batchService.requireBatchForUpdate(batchId, farmId);
        LocalDate selectedDate = dateValidation.resolve(date);
        dateValidation.validate(selectedDate, batch.getStartDate());
        DailyRecord existingRecord = recordRepository.findByBatchIdAndRecordDate(batchId, selectedDate)
                .orElse(null);
        int eventTotal = healthDeathsForDate(batchId, selectedDate);

        if (requestedMortalityCount != null && requestedMortalityCount < 0) {
            throw new BadRequestException("Mortality count cannot be negative");
        }

        // A null value is the new client contract. If the row predates canonical events, use its
        // old value once as migration input; after events exist, the event total always wins.
        int legacyTarget = requestedMortalityCount != null
                ? requestedMortalityCount
                : eventTotal == 0 && existingRecord != null
                    ? existingRecord.getMortalityCount()
                    : eventTotal;
        int missing = Math.max(0, legacyTarget - eventTotal);

        if (missing > 0) {
            validate(batch, selectedDate, missing, -missing, EventType.HEALTH_DEATH);
            CreateBatchEventRequest importedRequest = new CreateBatchEventRequest(
                    selectedDate,
                    EventType.HEALTH_DEATH,
                    "Daily Vitals health-death import",
                    "LEGACY_IMPORT",
                    missing,
                    "Imported from the legacy Daily Vitals mortality field; review its category.",
                    null,
                    UUID.nameUUIDFromBytes(("legacy-daily:" + batchId + ":" + selectedDate + ":" + missing)
                            .getBytes(StandardCharsets.UTF_8)),
                    null);
            persistCanonicalEvent(batch, selectedDate, fromRequest(handlerId, batchId, importedRequest), -missing);
            eventTotal += missing;
        }

        syncDailyRecord(selectedDate, batchId, eventTotal);
        return eventTotal;
    }

    private MortalityAccountingResult persistCanonicalEvent(Batch batch, LocalDate date,
                                                              BatchEvent event, int delta) {
        BatchEvent saved = eventRepository.save(event);
        int remainingPopulation = batch.getCurrentPopulation() + delta;
        batch.setCurrentPopulation(remainingPopulation);
        event.setPopulationAfter(remainingPopulation);
        batchRepository.save(batch);

        if (event.getEventType().isHealthMortality()) {
            int totalForDate = healthDeathsForDate(batch.getId(), date);
            syncDailyRecord(date, batch.getId(), totalForDate);
        }

        if (event.getEventType().isHealthMortality()) {
            publisher.publishEvent(new MortalityRecordedEvent(
                    saved.getId(), batch.getId(), event.getHandlerId(), date,
                    event.getAffectedCount(), remainingPopulation, event.getTitle()));
        }
        return new MortalityAccountingResult(saved, remainingPopulation);
    }

    private void syncDailyRecord(LocalDate date, Long batchId, int totalMortality) {
        recordRepository.findByBatchIdAndRecordDate(batchId, date).ifPresent(record -> {
            if (record.getMortalityCount() != totalMortality) {
                record.setMortalityCount(totalMortality);
                record.setUpdatedAt(Instant.now());
                DailyRecord persisted = recordRepository.save(record);
                publisher.publishEvent(new RecordCreatedEvent(persisted.getId(), batchId));
            }
        });
    }

    public int healthDeathsForDate(Long batchId, LocalDate date) {
        return Math.toIntExact(eventRepository
                .sumAffectedCountByBatchIdAndEventDateAndEventType(batchId, date, EventType.HEALTH_DEATH));
    }

    private void validate(Batch batch, LocalDate date, int affectedCount, int delta, EventType type) {
        dateValidation.validate(date, batch.getStartDate());
        if (affectedCount < 1 && type != EventType.COUNT_CORRECTION) {
            throw new BadRequestException("Population event count must be at least 1");
        }
        if (type == EventType.COUNT_CORRECTION && delta == 0) {
            throw new BadRequestException("A count correction must change the population");
        }
        int resultingPopulation = batch.getCurrentPopulation() + delta;
        if (resultingPopulation < 0) {
            throw new BadRequestException("This event would make the population negative; current alive: "
                    + batch.getCurrentPopulation());
        }
        if (resultingPopulation > batch.getInitialPopulation()) {
            throw new BadRequestException("This event would exceed the initial population of "
                    + batch.getInitialPopulation());
        }
    }

    private EventType normalize(EventType type) {
        return type == EventType.MORTALITY ? EventType.HEALTH_DEATH : type;
    }

    private BatchEvent fromRequest(Long handlerId, Long batchId, CreateBatchEventRequest request) {
        BatchEvent event = new BatchEvent();
        event.setBatchId(batchId);
        event.setHandlerId(handlerId);
        event.setOperationId(request.operationId());
        event.setEventDate(request.eventDate());
        event.setEventType(normalize(request.eventType()));
        event.setSeverityLabel(request.severityLabel());
        event.setAffectedCount(request.affectedCount());
        event.setPopulationDelta(-request.affectedCount());
        event.setTitle(request.title());
        event.setDetails(request.details());
        event.setTags(request.tags());
        return event;
    }

    public record MortalityAccountingResult(BatchEvent event, int remainingPopulation) {
    }
}
