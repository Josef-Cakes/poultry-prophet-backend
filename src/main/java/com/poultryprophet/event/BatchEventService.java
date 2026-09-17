package com.poultryprophet.event;

import com.poultryprophet.batch.Batch;
import com.poultryprophet.batch.BatchService;
import com.poultryprophet.common.DateValidationService;
import com.poultryprophet.common.QueryLimits;
import com.poultryprophet.event.dto.BatchEventResponse;
import com.poultryprophet.event.dto.CreateBatchEventRequest;
import com.poultryprophet.user.User;
import com.poultryprophet.user.UserRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class BatchEventService {

    private final BatchEventRepository eventRepository;
    private final BatchService batchService;
    private final MortalityAccountingService mortalityAccountingService;
    private final UserRepository userRepository;
    private final DateValidationService dateValidation;

    public BatchEventService(BatchEventRepository eventRepository,
                             BatchService batchService,
                             MortalityAccountingService mortalityAccountingService,
                             UserRepository userRepository,
                             DateValidationService dateValidation) {
        this.eventRepository = eventRepository;
        this.batchService = batchService;
        this.mortalityAccountingService = mortalityAccountingService;
        this.userRepository = userRepository;
        this.dateValidation = dateValidation;
    }

    @Transactional
    public BatchEventResponse create(Long batchId, Long farmId, Long handlerId,
                                     CreateBatchEventRequest req) {
        if (req.eventType().isPopulationLedgerEvent()) {
            MortalityAccountingService.MortalityAccountingResult result =
                    mortalityAccountingService.recordPopulationEvent(batchId, farmId, handlerId, req);
            String handlerName = userRepository.findById(handlerId)
                    .map(User::getFullName).orElse("Unknown");
            return BatchEventResponse.from(result.event(), handlerName, result.remainingPopulation());
        }

        Batch batch = batchService.requireBatchForUpdate(batchId, farmId);
        BatchEvent existing = req.operationId() == null
                ? null
                : eventRepository.findByOperationId(req.operationId()).orElse(null);
        if (existing != null) {
            if (!batchId.equals(existing.getBatchId())) {
                throw new com.poultryprophet.common.BadRequestException(
                        "operationId has already been used for another batch");
            }
            String existingHandlerName = userRepository.findById(existing.getHandlerId())
                    .map(User::getFullName).orElse("Unknown");
            return BatchEventResponse.from(existing, existingHandlerName,
                    existing.getPopulationAfter() != null
                            ? existing.getPopulationAfter()
                            : batch.getCurrentPopulation());
        }

        LocalDate date = dateValidation.resolve(req.eventDate());
        dateValidation.validate(date, batch.getStartDate());

        BatchEvent event = new BatchEvent();
        event.setBatchId(batchId);
        event.setHandlerId(handlerId);
        event.setOperationId(req.operationId() != null ? req.operationId() : UUID.randomUUID());
        event.setEventDate(date);
        event.setEventType(req.eventType());
        event.setSeverityLabel(req.severityLabel());
        event.setAffectedCount(req.affectedCount());
        event.setPopulationDelta(0);
        event.setPopulationAfter(batch.getCurrentPopulation());
        event.setTitle(req.title());
        event.setDetails(req.details());
        event.setTags(req.tags());
        BatchEvent saved = eventRepository.save(event);

        String handlerName = userRepository.findById(handlerId)
                .map(User::getFullName).orElse("Unknown");
        return BatchEventResponse.from(saved, handlerName);
    }

    @Transactional(readOnly = true)
    public List<BatchEventResponse> recent(Long batchId, Long farmId, int limit) {
        batchService.requireBatch(batchId, farmId);
        List<BatchEvent> events = eventRepository
                .findByBatchIdOrderByEventDateDescCreatedAtDesc(batchId,
                        PageRequest.of(0, QueryLimits.clamp(limit)))
                .stream().toList();
        Set<Long> hids = events.stream().map(BatchEvent::getHandlerId).collect(Collectors.toSet());
        Map<Long, String> names = userRepository.findAllById(hids).stream()
                .collect(Collectors.toMap(User::getId, User::getFullName));
        return events.stream()
                .map(e -> BatchEventResponse.from(e, names.getOrDefault(e.getHandlerId(), "Unknown")))
                .toList();
    }
}
