package com.poultryprophet.event;

import com.poultryprophet.batch.Batch;
import com.poultryprophet.batch.BatchService;
import com.poultryprophet.common.ConflictException;
import com.poultryprophet.common.DateValidationService;
import com.poultryprophet.common.QueryLimits;
import com.poultryprophet.event.dto.BatchEventResponse;
import com.poultryprophet.event.dto.BatchEventReportResponse;
import com.poultryprophet.event.dto.CreateBatchEventRequest;
import com.poultryprophet.finance.FinanceService;
import com.poultryprophet.user.User;
import com.poultryprophet.user.UserRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Collections;
import java.util.Comparator;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class BatchEventService {

    private final BatchEventRepository eventRepository;
    private final BatchService batchService;
    private final MortalityAccountingService mortalityAccountingService;
    private final UserRepository userRepository;
    private final DateValidationService dateValidation;
    private final FinanceService financeService;

    public BatchEventService(BatchEventRepository eventRepository,
                             BatchService batchService,
                             MortalityAccountingService mortalityAccountingService,
                             UserRepository userRepository,
                             DateValidationService dateValidation,
                             FinanceService financeService) {
        this.eventRepository = eventRepository;
        this.batchService = batchService;
        this.mortalityAccountingService = mortalityAccountingService;
        this.userRepository = userRepository;
        this.dateValidation = dateValidation;
        this.financeService = financeService;
    }

    @Transactional
    public BatchEventResponse create(Long batchId, Long farmId, Long handlerId,
                                     CreateBatchEventRequest req) {
        validateSalePayload(req);
        if (req.eventType().isPopulationLedgerEvent()) {
            MortalityAccountingService.MortalityAccountingResult result =
                    mortalityAccountingService.recordPopulationEvent(batchId, farmId, handlerId, req);
            if (req.eventType() == EventType.SALE) {
                financeService.createSaleIncome(farmId, handlerId, batchId,
                        result.event().getEventDate(), req.saleDetails(), req.operationId(), result.event().getId());
            }
            String handlerName = userRepository.findById(handlerId)
                    .map(User::getFullName).orElse("Unknown");
            return BatchEventResponse.from(result.event(), handlerName, result.remainingPopulation());
        }

        Batch batch = batchService.requireBatchForUpdate(batchId, farmId);
        batchService.ensureWritable(batch);
        LocalDate date = dateValidation.resolve(req.eventDate());
        BatchEvent existing = req.operationId() == null
                ? null
                : eventRepository.findByOperationId(req.operationId()).orElse(null);
        if (existing != null) {
            if (!batchId.equals(existing.getBatchId())
                    || (existing.getEventType() != null && existing.getEventType() != req.eventType())
                    || (existing.getEventDate() != null && !java.util.Objects.equals(existing.getEventDate(), date))
                    || existing.getAffectedCount() != req.affectedCount()
                    || !java.util.Objects.equals(existing.getSeverityLabel(), req.severityLabel())
                    || (existing.getTitle() != null && !java.util.Objects.equals(existing.getTitle(), req.title()))
                    || (existing.getDetails() != null && !java.util.Objects.equals(existing.getDetails(), req.details()))
                    || (existing.getTags() != null && !java.util.Objects.equals(existing.getTags(), req.tags()))) {
                throw new ConflictException(
                        "operationId has already been used for a different event record");
            }
            String existingHandlerName = userRepository.findById(existing.getHandlerId())
                    .map(User::getFullName).orElse("Unknown");
            return BatchEventResponse.from(existing, existingHandlerName,
                    existing.getPopulationAfter() != null
                            ? existing.getPopulationAfter()
                            : batch.getCurrentPopulation());
        }

        dateValidation.validate(date, batch.getHatchDate());

        BatchEvent event = new BatchEvent();
        event.setBatchId(batchId);
        event.setHandlerId(handlerId);
        event.setOperationId(req.operationId() != null ? req.operationId() : UUID.randomUUID());
        event.setEventDate(date);
        event.setEventType(req.eventType());
        event.setSeverityLabel(req.severityLabel());
        event.setSalePurpose(SalePurposeParser.parse(req.eventType(), req.tags()));
        event.setAffectedCount(req.affectedCount());
        event.setPopulationDelta(0);
        event.setPopulationAfter(batch.getCurrentPopulation());
        event.setTitle(req.title());
        event.setDetails(req.details());
        event.setTags(req.tags());
        event.setOccurredAt(req.occurredAt());
        if (req.saleDetails() != null) {
            event.setSaleAmount(req.saleDetails().amount());
            event.setSaleCurrency(req.saleDetails().currency() == null || req.saleDetails().currency().isBlank()
                    ? "PHP" : req.saleDetails().currency().trim().toUpperCase());
            event.setSaleBuyer(req.saleDetails().buyer());
            event.setSaleReference(req.saleDetails().reference());
        }
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

    @Transactional(readOnly = true)
    public com.poultryprophet.common.PageResponse<BatchEventResponse> page(Long batchId, Long farmId,
                                                                             LocalDate start, LocalDate end,
                                                                             EventType type, Long handlerId,
                                                                             String concernLevel,
                                                                             boolean populationOnly, String search,
                                                                             int page, int size) {
        batchService.requireBatch(batchId, farmId);
        Specification<BatchEvent> spec = eventSpecification(batchId, start, end, type, handlerId,
                concernLevel, populationOnly, search);
        Page<BatchEvent> result = eventRepository.findAll(spec, PageRequest.of(Math.max(0, page),
                Math.max(1, Math.min(size, 100)), Sort.by(Sort.Order.desc("eventDate"),
                        Sort.Order.desc("createdAt"), Sort.Order.desc("id"))));
        Map<Long, String> names = handlerNames(result.getContent());
        return com.poultryprophet.common.PageResponse.from(result,
                event -> BatchEventResponse.from(event, names.getOrDefault(event.getHandlerId(), "Unknown")));
    }

    @Transactional(readOnly = true)
    public BatchEventReportResponse report(Long batchId, Long farmId, LocalDate start, LocalDate end,
                                           EventType type, Long handlerId, String concernLevel,
                                           boolean populationOnly, String search) {
        batchService.requireBatch(batchId, farmId);
        List<BatchEvent> events = eventRepository.findAll(eventSpecification(batchId, start, end, type,
                handlerId, concernLevel, populationOnly, search), Sort.by(Sort.Order.asc("eventDate"),
                Sort.Order.asc("createdAt"), Sort.Order.asc("id")));
        long deaths = events.stream().filter(event -> event.getEventType() != null && event.getEventType().isDeathEvent()).count();
        long birdsLost = events.stream().filter(event -> event.getEventType() != null && event.getEventType().isDeathEvent())
                .mapToLong(BatchEvent::getAffectedCount).sum();
        long health = events.stream().filter(event -> event.getEventType() == EventType.HEALTH_CONCERN).count();
        long movements = events.stream().filter(event -> event.getEventType() != null
                && event.getEventType().isPopulationLedgerEvent() && !event.getEventType().isDeathEvent()).count();
        long vaccine = events.stream().filter(event -> event.getEventType() == EventType.VACCINE_MEDICINE).count();
        Map<String, Long> category = new LinkedHashMap<>();
        Map<String, Long> concern = new LinkedHashMap<>();
        Map<String, Long> dates = new LinkedHashMap<>();
        for (BatchEvent event : events) {
            String categoryKey = event.getEventType() == null ? "UNKNOWN" : event.getEventType().name();
            category.merge(categoryKey, 1L, Long::sum);
            concern.merge(normalizeConcern(event.getSeverityLabel()), 1L, Long::sum);
            if (event.getEventDate() != null) dates.merge(event.getEventDate().toString(), 1L, Long::sum);
        }
        List<BatchEventReportResponse.CountPoint> overTime = dates.entrySet().stream()
                .map(entry -> new BatchEventReportResponse.CountPoint(entry.getKey(), entry.getValue())).toList();
        return new BatchEventReportResponse(events.size(), deaths, birdsLost, health, movements, vaccine,
                overTime, category, concern);
    }

    private Specification<BatchEvent> eventSpecification(Long batchId, LocalDate start, LocalDate end,
                                                          EventType type, Long handlerId, String concernLevel,
                                                          boolean populationOnly, String search) {
        return (root, query, cb) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("batchId"), batchId));
            if (start != null) predicates.add(cb.greaterThanOrEqualTo(root.get("eventDate"), start));
            if (end != null) predicates.add(cb.lessThanOrEqualTo(root.get("eventDate"), end));
            if (type != null) predicates.add(cb.equal(root.get("eventType"), type));
            if (handlerId != null) predicates.add(cb.equal(root.get("handlerId"), handlerId));
            if (populationOnly) predicates.add(root.get("populationDelta").isNotNull());
            if (search != null && !search.isBlank()) {
                String term = "%" + search.trim().toLowerCase() + "%";
                predicates.add(cb.or(cb.like(cb.lower(root.get("title")), term),
                        cb.like(cb.lower(root.get("details")), term)));
            }
            if (concernLevel != null && !concernLevel.isBlank()) {
                String level = concernLevel.trim().toUpperCase();
                List<String> raw = switch (level) {
                    case "LOW" -> List.of("LOW", "MINOR");
                    case "MEDIUM" -> List.of("MEDIUM", "MODERATE");
                    case "HIGH" -> List.of("HIGH", "MAJOR");
                    default -> List.of();
                };
                if (!raw.isEmpty()) predicates.add(root.get("severityLabel").in(raw));
                else if ("UNRATED".equals(level)) predicates.add(cb.or(cb.isNull(root.get("severityLabel")), cb.equal(root.get("severityLabel"), "")));
            }
            return cb.and(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
    }

    private Map<Long, String> handlerNames(List<BatchEvent> events) {
        Set<Long> hids = events.stream().map(BatchEvent::getHandlerId).collect(Collectors.toSet());
        return userRepository.findAllById(hids).stream().collect(Collectors.toMap(User::getId, User::getFullName));
    }

    private String normalizeConcern(String value) {
        if (value == null || value.isBlank()) return "UNRATED";
        return switch (value.trim().toUpperCase()) {
            case "MINOR", "LOW" -> "LOW";
            case "MODERATE", "MEDIUM" -> "MEDIUM";
            case "MAJOR", "HIGH" -> "HIGH";
            default -> "UNRATED";
        };
    }

    private void validateSalePayload(CreateBatchEventRequest req) {
        if (req.eventType() == EventType.SALE) {
            if (req.saleDetails() == null || req.saleDetails().amount() == null
                    || req.saleDetails().amount().signum() <= 0) {
                throw new com.poultryprophet.common.BadRequestException(
                        "A total sale amount greater than zero is required for a sale event");
            }
            return;
        }
        if (req.saleDetails() != null) {
            throw new com.poultryprophet.common.BadRequestException(
                    "Sale details are only valid for sale events");
        }
    }
}
