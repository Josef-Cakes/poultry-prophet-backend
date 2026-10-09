package com.poultryprophet.sexcomposition;

import com.poultryprophet.batch.Batch;
import com.poultryprophet.batch.BatchService;
import com.poultryprophet.common.BadRequestException;
import com.poultryprophet.common.ConflictException;
import com.poultryprophet.event.BatchEvent;
import com.poultryprophet.event.BatchEventRepository;
import com.poultryprophet.population.PopulationProjection;
import com.poultryprophet.population.PopulationProjectionService;
import com.poultryprophet.sexcomposition.dto.CreateSexCompositionRequest;
import com.poultryprophet.sexcomposition.dto.SexCompositionResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

@Service
public class BatchSexCompositionService {
    private final BatchSexCompositionRepository repository;
    private final BatchService batchService;
    private final BatchEventRepository eventRepository;
    private final PopulationProjectionService projectionService;
    private final SexCompositionProjectionService sexProjectionService;
    private final ZoneId zoneId;

    public BatchSexCompositionService(BatchSexCompositionRepository repository, BatchService batchService,
                                      BatchEventRepository eventRepository,
                                      PopulationProjectionService projectionService,
                                      SexCompositionProjectionService sexProjectionService) {
        this.repository = repository; this.batchService = batchService; this.eventRepository = eventRepository;
        this.projectionService = projectionService; this.sexProjectionService = sexProjectionService; this.zoneId = ZoneId.of("Asia/Manila");
    }

    @Transactional(readOnly = true)
    public SexCompositionResponse current(Long batchId, Long farmId) {
        Batch batch = batchService.requireBatch(batchId, farmId);
        BatchSexComposition baseline = repository
                .findFirstByBatchIdAndFarmIdAndObservedOnLessThanEqualOrderByObservedOnDescCreatedAtDesc(
                        batchId, farmId, LocalDate.now(zoneId)).orElse(null);
        if (baseline == null) return null;
        return SexCompositionResponse.fromProjected(baseline,
                sexProjectionService.project(batch, farmId, LocalDate.now(zoneId)));
    }

    @Transactional(readOnly = true)
    public List<SexCompositionResponse> history(Long batchId, Long farmId) {
        batchService.requireBatch(batchId, farmId);
        return repository.findByBatchIdAndFarmIdOrderByObservedOnDescCreatedAtDesc(batchId, farmId)
                .stream().map(SexCompositionResponse::from).toList();
    }

    @Transactional
    public SexCompositionResponse record(Long batchId, Long farmId, Long userId, CreateSexCompositionRequest request) {
        Batch batch = batchService.requireWritableBatch(batchId, farmId);
        LocalDate observedOn = request.observedOn();
        if (observedOn.isAfter(LocalDate.now(zoneId))) throw new BadRequestException("Observation date cannot be in the future");
        if (observedOn.isBefore(batch.getStartDate())) throw new BadRequestException("Observation date cannot be before the hatch date");
        UUID operationId = request.operationId() == null ? UUID.randomUUID() : request.operationId();
        BatchSexComposition existing = repository.findByOperationId(operationId).orElse(null);
        if (existing != null) {
            if (!farmId.equals(existing.getFarmId()) || !batchId.equals(existing.getBatchId())) throw new ConflictException("This operation ID belongs to another batch");
            return SexCompositionResponse.from(existing);
        }
        List<BatchEvent> events = eventRepository.findByBatchIdAndEventDateBetweenOrderByEventDateAscCreatedAtAsc(batchId, batch.getStartDate(), observedOn);
        PopulationProjection projection = projectionService.project(batch, events, observedOn, zoneId);
        Integer population = projection.validPopulation();
        if (population == null) throw new ConflictException("Population history needs reconciliation before sexing this batch");
        int total = request.maleCount() + request.femaleCount() + request.unclassifiedCount();
        if (total != population) throw new BadRequestException("Male + female + unclassified must equal " + population + " birds");
        BatchSexComposition current = repository.findByBatchIdAndFarmIdAndStatus(batchId, farmId, "CURRENT").orElse(null);
        if (current != null) { current.setStatus("SUPERSEDED"); repository.save(current); }
        BatchSexComposition row = new BatchSexComposition();
        row.setFarmId(farmId); row.setBatchId(batchId); row.setObservedOn(observedOn);
        row.setPopulationAsOfObservation(population); row.setMaleCount(request.maleCount()); row.setFemaleCount(request.femaleCount());
        row.setUnclassifiedCount(request.unclassifiedCount()); row.setRecordedBy(userId);
        row.setRevisionReason(trim(request.revisionReason())); row.setNotes(trim(request.notes())); row.setOperationId(operationId);
        row.setBaselineEventId(eventRepository.findTopByBatchIdAndEventDateLessThanEqualOrderByIdDesc(batchId, observedOn)
                .map(BatchEvent::getId).orElse(0L));
        row.setSupersedesRecordId(current == null ? null : current.getId()); row.setStatus("CURRENT");
        return SexCompositionResponse.from(repository.save(row));
    }

    private String trim(String value) { return value == null || value.isBlank() ? null : value.trim(); }
}
