package com.poultryprophet.event;

import com.poultryprophet.batch.Batch;
import com.poultryprophet.batch.BatchRepository;
import com.poultryprophet.batch.BatchService;
import com.poultryprophet.common.BadRequestException;
import com.poultryprophet.common.DateValidationService;
import com.poultryprophet.event.dto.CreateBatchEventRequest;
import com.poultryprophet.population.PopulationProjection;
import com.poultryprophet.population.PopulationProjectionService;
import com.poultryprophet.record.DailyRecord;
import com.poultryprophet.record.DailyRecordRepository;
import com.poultryprophet.record.event.RecordCreatedEvent;
import com.poultryprophet.sexcomposition.SexCompositionProjectionService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
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
    private final PopulationProjectionService populationProjectionService;
    private final SexCompositionProjectionService sexCompositionProjectionService;

    public MortalityAccountingService(BatchService batchService,
                                      BatchRepository batchRepository,
                                      BatchEventRepository eventRepository,
                                      DailyRecordRepository recordRepository,
                                      ApplicationEventPublisher publisher,
                                      DateValidationService dateValidation,
                                      PopulationProjectionService populationProjectionService,
                                      SexCompositionProjectionService sexCompositionProjectionService) {
        this.batchService = batchService;
        this.batchRepository = batchRepository;
        this.eventRepository = eventRepository;
        this.recordRepository = recordRepository;
        this.publisher = publisher;
        this.dateValidation = dateValidation;
        this.populationProjectionService = populationProjectionService;
        this.sexCompositionProjectionService = sexCompositionProjectionService;
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
        batchService.ensureWritable(batch);

        if (request.operationId() != null) {
            BatchEvent existing = eventRepository.findByOperationId(request.operationId()).orElse(null);
            if (existing != null) {
                if (!batchId.equals(existing.getBatchId())
                        || (existing.getEventType() != null && existing.getEventType() != type)
                        || (existing.getEventDate() != null && !java.util.Objects.equals(existing.getEventDate(), date))
                        || (existing.getAffectedCount() != 0 && existing.getAffectedCount() != request.affectedCount())
                        || !java.util.Objects.equals(existing.getMaleDelta(), maleDelta(request))
                        || !java.util.Objects.equals(existing.getFemaleDelta(), femaleDelta(request))
                        || !java.util.Objects.equals(existing.getUnclassifiedDelta(), unclassifiedDelta(request))
                        || (existing.getTitle() != null && !java.util.Objects.equals(existing.getTitle(), request.title()))
                        || (existing.getDetails() != null && !java.util.Objects.equals(existing.getDetails(), request.details()))
                        || (existing.getTags() != null && !java.util.Objects.equals(existing.getTags(), request.tags()))) {
                    throw new BadRequestException("operationId has already been used for a different population event");
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
        applySexAllocation(batch, farmId, date, type, delta, request, event);
        event.setTitle(request.title());
        event.setDetails(request.details());
        event.setTags(request.tags());

        validateChronologicalProjection(batch, date, event);

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
        batchService.ensureWritable(batch);
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
        int remainingPopulation = Math.toIntExact((long) batch.getCurrentPopulation() + delta);
        batch.setCurrentPopulation(remainingPopulation);
        event.setPopulationAfter(remainingPopulation);
        batchRepository.save(batch);

        if (event.getEventType().isHealthMortality()) {
            int totalForDate = healthDeathsForDate(batch.getId(), date);
            syncDailyRecord(date, batch.getId(), totalForDate);
        }

        if (event.getEventType().isDeathEvent()) {
            publisher.publishEvent(new MortalityRecordedEvent(
                    saved.getId(), batch.getId(), event.getHandlerId(), date,
                    event.getEventType(), event.getAffectedCount(), remainingPopulation, event.getTitle()));
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
        long resultingPopulation = (long) batch.getCurrentPopulation() + delta;
        if (resultingPopulation < 0) {
            throw new BadRequestException("This event would make the population negative; current alive: "
                    + batch.getCurrentPopulation());
        }
        if (resultingPopulation > batch.getInitialPopulation()) {
            throw new BadRequestException("This event would exceed the initial population of "
                    + batch.getInitialPopulation());
        }
    }

    /**
     * A backdated event must be safe at every chronological point, not merely against the count
     * currently stored on the batch row. This protects the append-only ledger from creating a
     * temporary negative balance that a later event happens to hide.
     */
    private void validateChronologicalProjection(Batch batch, LocalDate date, BatchEvent candidate) {
        LocalDate projectionEnd = dateValidation.today();
        if (projectionEnd == null || projectionEnd.isBefore(date)) projectionEnd = date;
        List<BatchEvent> existing = eventRepository
                .findByBatchIdAndEventDateBetweenOrderByEventDateAscCreatedAtAsc(
                        batch.getId(), batch.getStartDate(), projectionEnd);
        PopulationProjection existingProjection = populationProjectionService.project(
                batch, existing, projectionEnd, ZoneId.of("Asia/Manila"));
        if (existingProjection.reconciliationRequired()) {
            throw new BadRequestException("This batch needs population reconciliation before new events can sync: "
                    + existingProjection.reconciliationMessage());
        }

        List<BatchEvent> withCandidate = new ArrayList<>(existing);
        withCandidate.add(candidate);
        PopulationProjection projection = populationProjectionService.projectLedger(
                batch, withCandidate, projectionEnd, ZoneId.of("Asia/Manila"));
        if (projection.reconciliationRequired()) {
            throw new BadRequestException("This event would create an invalid chronological population ledger: "
                    + projection.reconciliationMessage());
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
        event.setMaleDelta(maleDelta(request));
        event.setFemaleDelta(femaleDelta(request));
        event.setUnclassifiedDelta(unclassifiedDelta(request));
        event.setTitle(request.title());
        event.setDetails(request.details());
        event.setTags(request.tags());
        event.setSalePurpose(SalePurposeParser.parse(request.eventType(), request.tags()));
        return event;
    }

    private void applySexAllocation(Batch batch, Long farmId, LocalDate date, EventType type, int populationDelta,
                                    CreateBatchEventRequest request, BatchEvent event) {
        CreateBatchEventRequest.SexAllocation allocation = request.sexAllocation();
        boolean any = allocation != null
                && (allocation.maleDelta() != null || allocation.femaleDelta() != null
                || allocation.unclassifiedDelta() != null);
        boolean hasBaseline = sexCompositionProjectionService != null
                && sexCompositionProjectionService.hasBaseline(batch.getId(), farmId, date);
        if (!any) {
            if (hasBaseline) {
                throw new BadRequestException("Select whether the affected birds were male, female, unclassified, or mixed");
            }
            return;
        }
        if (!allocation.isComplete()) {
            throw new BadRequestException("Male, female, and unclassified allocations must all be provided");
        }
        if (allocation.totalDelta() != populationDelta) {
            throw new BadRequestException("Male + female + unclassified changes must equal the population change");
        }
        if (type != EventType.COUNT_CORRECTION) {
            boolean addition = populationDelta > 0;
            if ((addition && (allocation.maleDelta() < 0 || allocation.femaleDelta() < 0 || allocation.unclassifiedDelta() < 0))
                    || (!addition && (allocation.maleDelta() > 0 || allocation.femaleDelta() > 0 || allocation.unclassifiedDelta() > 0))) {
                throw new BadRequestException("Sex allocations must use the same direction as the population event");
            }
        }
        if (sexCompositionProjectionService == null) {
            throw new BadRequestException("Sex composition validation is unavailable; try again");
        }
        event.setMaleDelta(allocation.maleDelta());
        event.setFemaleDelta(allocation.femaleDelta());
        event.setUnclassifiedDelta(allocation.unclassifiedDelta());
        sexCompositionProjectionService.validateCandidate(batch, farmId, date, event);
    }

    private Integer maleDelta(CreateBatchEventRequest request) {
        return request.sexAllocation() == null ? null : request.sexAllocation().maleDelta();
    }

    private Integer femaleDelta(CreateBatchEventRequest request) {
        return request.sexAllocation() == null ? null : request.sexAllocation().femaleDelta();
    }

    private Integer unclassifiedDelta(CreateBatchEventRequest request) {
        return request.sexAllocation() == null ? null : request.sexAllocation().unclassifiedDelta();
    }

    public record MortalityAccountingResult(BatchEvent event, int remainingPopulation) {
    }
}
