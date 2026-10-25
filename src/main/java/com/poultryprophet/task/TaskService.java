package com.poultryprophet.task;

import com.poultryprophet.batch.BatchService;
import com.poultryprophet.common.BadRequestException;
import com.poultryprophet.common.NotFoundException;
import com.poultryprophet.common.ConflictException;
import com.poultryprophet.common.PageResponse;
import com.poultryprophet.incubation.IncubationService;
import com.poultryprophet.task.dto.CreateTaskRequest;
import com.poultryprophet.task.dto.TaskResponse;
import com.poultryprophet.task.dto.TaskSummaryResponse;
import com.poultryprophet.task.dto.HandlerTaskQueueSummary;
import com.poultryprophet.task.dto.UpdateTaskRequest;
import com.poultryprophet.task.dto.UpdateTaskStatusRequest;
import com.poultryprophet.user.Role;
import com.poultryprophet.user.User;
import com.poultryprophet.user.UserRepository;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class TaskService {
    private final HandlerTaskRepository taskRepository;
    private final TaskStatusHistoryRepository historyRepository;
    private final BatchService batchService;
    private final IncubationService incubationService;
    private final UserRepository userRepository;
    private final Clock applicationClock;

    private enum TaskOrdering {
        OPEN,
        COMPLETED,
        CANCELLED
    }

    public TaskService(HandlerTaskRepository taskRepository, TaskStatusHistoryRepository historyRepository,
                       BatchService batchService, IncubationService incubationService,
                       UserRepository userRepository, Clock applicationClock) {
        this.taskRepository = taskRepository;
        this.historyRepository = historyRepository;
        this.batchService = batchService;
        this.incubationService = incubationService;
        this.userRepository = userRepository;
        this.applicationClock = applicationClock;
    }

    @Transactional
    public TaskResponse create(Long farmId, Long managerId, CreateTaskRequest request) {
        requireFarm(farmId);
        TaskAssignmentMode assignmentMode = request.assignmentMode() == null
                ? (request.assignedHandlerId() == null ? TaskAssignmentMode.ALL_HANDLERS : TaskAssignmentMode.SPECIFIC_HANDLER)
                : request.assignmentMode();
        validateReferences(farmId, request.batchId(), request.incubationCycleId(), request.assignedHandlerId(), assignmentMode);
        HandlerTask task = new HandlerTask();
        task.setFarmId(farmId);
        task.setBatchId(request.batchId());
        task.setIncubationCycleId(request.incubationCycleId());
        task.setTitle(request.title().trim());
        task.setInstructions(trim(request.instructions()));
        task.setAssignedHandlerId(assignmentMode == TaskAssignmentMode.SPECIFIC_HANDLER ? request.assignedHandlerId() : null);
        task.setAssignmentScope(scopeFor(assignmentMode, request.batchId()));
        task.setAssignedManagerId(managerId);
        task.setDueAt(request.dueAt());
        task.setPriority(request.priority() == null ? TaskPriority.NORMAL : request.priority());
        task.setUpdatedAt(Instant.now(applicationClock));
        HandlerTask saved = taskRepository.save(task);
        recordStatus(saved, managerId, TaskStatus.TODO, null);
        return response(saved);
    }

    @Transactional(readOnly = true)
    public PageResponse<TaskResponse> page(Long farmId, Long userId, boolean manager,
                                           TaskFilter requestedFilter, int page, int size) {
        requireFarm(farmId);
        TaskFilter filter = requestedFilter == null ? TaskFilter.defaults() : requestedFilter.normalized();
        Specification<HandlerTask> query = orderedSpecification(
                specification(farmId, userId, manager, filter, true), orderingFor(filter.view()));
        Page<HandlerTask> result = taskRepository.findAll(query,
                PageRequest.of(Math.max(0, page), Math.max(1, Math.min(size, 50))));
        return PageResponse.from(result, this::response);
    }

    @Transactional(readOnly = true)
    public PageResponse<TaskResponse> handlerQueue(Long farmId, Long userId,
                                                   HandlerTaskQueueBucket bucket, int page, int size) {
        requireFarm(farmId);
        HandlerTaskQueueBucket requested = bucket == null ? HandlerTaskQueueBucket.NOW : bucket;
        TaskFilter filter = new TaskFilter(
                requested == HandlerTaskQueueBucket.COMPLETED ? TaskView.COMPLETED : TaskView.OPEN,
                TaskDueBucket.ALL, null, null, null, null, null, null, null);
        Specification<HandlerTask> query = orderedSpecification(
                specification(farmId, userId, false, filter, false).and(queueBucket(requested)),
                requested == HandlerTaskQueueBucket.COMPLETED ? TaskOrdering.COMPLETED : TaskOrdering.OPEN);
        Page<HandlerTask> result = taskRepository.findAll(query,
                PageRequest.of(Math.max(0, page), Math.max(1, Math.min(size, 20))));
        return PageResponse.from(result, this::response);
    }

    @Transactional(readOnly = true)
    public HandlerTaskQueueSummary handlerQueueSummary(Long farmId, Long userId) {
        requireFarm(farmId);
        TaskFilter filter = new TaskFilter(TaskView.OPEN, TaskDueBucket.ALL, null, null, null, null, null, null, null);
        Specification<HandlerTask> base = specification(farmId, userId, false, filter, false);
        Specification<HandlerTask> open = base.and(statuses(TaskStatus.TODO, TaskStatus.IN_PROGRESS, TaskStatus.BLOCKED));
        long now = taskRepository.count(open.and(queueBucket(HandlerTaskQueueBucket.NOW)));
        long upcoming = taskRepository.count(open.and(queueBucket(HandlerTaskQueueBucket.UPCOMING)));
        long completed = taskRepository.count(base.and(statuses(TaskStatus.COMPLETED)));
        long reminders = taskRepository.count(open.and(vaccinationSource()).and(queueBucket(HandlerTaskQueueBucket.NOW)));
        return new HandlerTaskQueueSummary(now, upcoming, completed, reminders);
    }

    /** Compatibility method for internal callers while HTTP consumers migrate to page(). */
    @Transactional(readOnly = true)
    public List<TaskResponse> list(Long farmId, Long handlerId, boolean mine) {
        return page(farmId, handlerId, !mine, TaskFilter.defaults(), 0, 100).items();
    }

    /** Offline working set: all actionable tasks plus a bounded completed history. */
    @Transactional(readOnly = true)
    public List<TaskResponse> offlineTasks(Long farmId, Long userId, boolean manager) {
        requireFarm(farmId);
        TaskFilter open = new TaskFilter(TaskView.OPEN, TaskDueBucket.ALL, null, null, null, null, null, null, null);
        List<TaskResponse> actionable = taskRepository
                .findAll(orderedSpecification(specification(farmId, userId, manager, open, true), TaskOrdering.OPEN))
                .stream().map(this::response).toList();
        List<TaskResponse> scheduledVaccinations = manager ? List.of() : taskRepository
                .findAll(orderedSpecification(futureVaccinationWorkingSet(farmId, userId), TaskOrdering.OPEN))
                .stream().map(this::response).toList();
        TaskFilter completed = new TaskFilter(TaskView.COMPLETED, TaskDueBucket.ALL, null, null, null, null, null, null, null);
        List<TaskResponse> recentCompleted = taskRepository
                .findAll(orderedSpecification(specification(farmId, userId, manager, completed, true), TaskOrdering.COMPLETED),
                        PageRequest.of(0, 20))
                .stream().map(this::response).toList();
        java.util.LinkedHashMap<Long, TaskResponse> workingSet = new java.util.LinkedHashMap<>();
        java.util.stream.Stream.of(actionable, scheduledVaccinations, recentCompleted)
                .flatMap(List::stream)
                .forEach(task -> workingSet.putIfAbsent(task.id(), task));
        return List.copyOf(workingSet.values());
    }

    @Transactional(readOnly = true)
    public TaskSummaryResponse summary(Long farmId, Long userId, boolean manager, TaskFilter requestedFilter) {
        requireFarm(farmId);
        TaskFilter filter = (requestedFilter == null ? TaskFilter.defaults() : requestedFilter.normalized());
        TaskFilter facetFilter = new TaskFilter(TaskView.ALL, TaskDueBucket.ALL, filter.batchId(), filter.farmWide(),
                filter.assignedHandlerId(), filter.priority(), filter.sourceType(), filter.assignmentScope(), filter.search());
        Specification<HandlerTask> base = specification(farmId, userId, manager, facetFilter, false);
        Specification<HandlerTask> open = base.and(statuses(TaskStatus.TODO, TaskStatus.IN_PROGRESS, TaskStatus.BLOCKED));
        Instant now = Instant.now(applicationClock);
        Instant tomorrow = LocalDate.now(applicationClock).plusDays(1)
                .atStartOfDay(applicationClock.getZone()).toInstant();
        long late = taskRepository.count(open.and(dueBefore(now)));
        long today = taskRepository.count(open.and(dueBetween(now, tomorrow)));
        long upcoming = taskRepository.count(open.and(dueOnOrAfter(tomorrow)));
        long noDue = taskRepository.count(open.and(dueIsNull()));
        return new TaskSummaryResponse(
                late + today + upcoming + noDue,
                late,
                today,
                upcoming,
                noDue,
                taskRepository.count(base.and(statuses(TaskStatus.COMPLETED))),
                taskRepository.count(base.and(statuses(TaskStatus.CANCELLED)))
        );
    }

    private TaskOrdering orderingFor(TaskView view) {
        return switch (view) {
            case COMPLETED -> TaskOrdering.COMPLETED;
            case CANCELLED -> TaskOrdering.CANCELLED;
            case OPEN, ALL -> TaskOrdering.OPEN;
        };
    }

    /**
     * Applies task ordering through Criteria expressions instead of Spring Data's Sort null-handling.
     * Spring Data JPA currently throws UnsupportedOperationException when a Criteria-backed query is
     * given Sort.Order.nullsLast(). The explicit CASE expressions also keep the ordering stable across
     * pages and make task priority follow the business order rather than alphabetical enum text order.
     */
    private Specification<HandlerTask> orderedSpecification(Specification<HandlerTask> base,
                                                             TaskOrdering ordering) {
        return (root, query, cb) -> {
            Predicate predicate = base.toPredicate(root, query, cb);
            if (!isCountQuery(query)) {
                query.orderBy(criteriaOrders(root, cb, ordering));
            }
            return predicate;
        };
    }

    private boolean isCountQuery(CriteriaQuery<?> query) {
        return Long.class.equals(query.getResultType()) || long.class.equals(query.getResultType());
    }

    private List<Order> criteriaOrders(Root<HandlerTask> root,
                                       jakarta.persistence.criteria.CriteriaBuilder cb,
                                       TaskOrdering ordering) {
        if (ordering == TaskOrdering.COMPLETED) {
            return List.of(cb.desc(root.get("completedAt")), cb.desc(root.get("id")));
        }
        if (ordering == TaskOrdering.CANCELLED) {
            return List.of(cb.desc(root.get("updatedAt")), cb.desc(root.get("id")));
        }

        Expression<Integer> dueNullRank = cb.<Integer>selectCase()
                .when(cb.isNull(root.get("dueAt")), 1)
                .otherwise(0);
        Expression<Integer> priorityRank = cb.<Integer>selectCase()
                .when(cb.equal(root.get("priority"), TaskPriority.URGENT), 1)
                .when(cb.equal(root.get("priority"), TaskPriority.HIGH), 2)
                .when(cb.equal(root.get("priority"), TaskPriority.NORMAL), 3)
                .when(cb.equal(root.get("priority"), TaskPriority.LOW), 4)
                .otherwise(5);
        return List.of(cb.asc(dueNullRank), cb.asc(root.get("dueAt")), cb.asc(priorityRank),
                cb.desc(root.get("createdAt")), cb.desc(root.get("id")));
    }

    private Specification<HandlerTask> specification(Long farmId, Long userId, boolean manager,
                                                      TaskFilter filter, boolean includeViewAndDue) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("farmId"), farmId));
            Instant now = Instant.now(applicationClock);
            if (!manager) {
                predicates.add(cb.and(
                        cb.or(cb.equal(root.get("assignedHandlerId"), userId),
                                root.get("assignmentScope").in("BATCH_TEAM", "FARM_TEAM")),
                        cb.or(cb.isNull(root.get("visibleFrom")), cb.lessThanOrEqualTo(root.get("visibleFrom"), now))
                ));
            } else if (filter.assignedHandlerId() != null) {
                predicates.add(cb.equal(root.get("assignedHandlerId"), filter.assignedHandlerId()));
            }
            if (filter.batchId() != null) predicates.add(cb.equal(root.get("batchId"), filter.batchId()));
            if (Boolean.TRUE.equals(filter.farmWide())) predicates.add(cb.isNull(root.get("batchId")));
            if (filter.priority() != null) predicates.add(cb.equal(root.get("priority"), filter.priority()));
            if (filter.sourceType() != null) predicates.add(cb.equal(cb.upper(root.get("sourceType")), filter.sourceType().toUpperCase()));
            if (filter.assignmentScope() != null) predicates.add(cb.equal(root.get("assignmentScope"), filter.assignmentScope()));
            if (filter.search() != null) predicates.add(cb.like(cb.lower(root.get("title")), "%" + filter.search().toLowerCase() + "%"));
            if (includeViewAndDue) {
                addViewPredicates(predicates, cb, root, filter.view());
                if (filter.view() == TaskView.OPEN || filter.view() == TaskView.ALL) {
                    addDuePredicates(predicates, cb, root, filter.dueBucket(), now);
                }
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private void addViewPredicates(List<Predicate> predicates, jakarta.persistence.criteria.CriteriaBuilder cb,
                                   jakarta.persistence.criteria.Root<HandlerTask> root, TaskView view) {
        if (view == TaskView.OPEN) predicates.add(root.get("status").in(TaskStatus.TODO, TaskStatus.IN_PROGRESS, TaskStatus.BLOCKED));
        else if (view == TaskView.COMPLETED) predicates.add(cb.equal(root.get("status"), TaskStatus.COMPLETED));
        else if (view == TaskView.CANCELLED) predicates.add(cb.equal(root.get("status"), TaskStatus.CANCELLED));
        else if (view == TaskView.ALL) predicates.add(root.get("status").in(
                TaskStatus.TODO, TaskStatus.IN_PROGRESS, TaskStatus.BLOCKED, TaskStatus.COMPLETED));
    }

    private void addDuePredicates(List<Predicate> predicates, jakarta.persistence.criteria.CriteriaBuilder cb,
                                  jakarta.persistence.criteria.Root<HandlerTask> root, TaskDueBucket bucket, Instant now) {
        if (bucket == null || bucket == TaskDueBucket.ALL) return;
        if (bucket == TaskDueBucket.LATE) predicates.add(cb.lessThan(root.get("dueAt"), now));
        else if (bucket == TaskDueBucket.NO_DUE) predicates.add(cb.isNull(root.get("dueAt")));
        else if (bucket == TaskDueBucket.TODAY) {
            Instant tomorrow = LocalDate.now(applicationClock).plusDays(1).atStartOfDay(applicationClock.getZone()).toInstant();
            predicates.add(cb.and(cb.greaterThanOrEqualTo(root.get("dueAt"), now), cb.lessThan(root.get("dueAt"), tomorrow)));
        } else if (bucket == TaskDueBucket.UPCOMING) {
            Instant tomorrow = LocalDate.now(applicationClock).plusDays(1).atStartOfDay(applicationClock.getZone()).toInstant();
            predicates.add(cb.greaterThanOrEqualTo(root.get("dueAt"), tomorrow));
        }
    }

    private Specification<HandlerTask> statuses(TaskStatus... statuses) {
        return (root, query, cb) -> root.get("status").in((Object[]) statuses);
    }

    private Specification<HandlerTask> dueBefore(Instant value) {
        return (root, query, cb) -> cb.lessThan(root.get("dueAt"), value);
    }

    private Specification<HandlerTask> dueBetween(Instant start, Instant end) {
        return (root, query, cb) -> cb.and(cb.greaterThanOrEqualTo(root.get("dueAt"), start), cb.lessThan(root.get("dueAt"), end));
    }

    private Specification<HandlerTask> dueOnOrAfter(Instant value) {
        return (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("dueAt"), value);
    }

    private Specification<HandlerTask> dueIsNull() {
        return (root, query, cb) -> cb.isNull(root.get("dueAt"));
    }

    private Specification<HandlerTask> queueBucket(HandlerTaskQueueBucket bucket) {
        return (root, query, cb) -> {
            if (bucket == HandlerTaskQueueBucket.COMPLETED) {
                return cb.equal(root.get("status"), TaskStatus.COMPLETED);
            }
            Instant tomorrow = LocalDate.now(applicationClock).plusDays(1)
                    .atStartOfDay(applicationClock.getZone()).toInstant();
            jakarta.persistence.criteria.Expression<String> source = cb.upper(root.get("sourceType"));
            jakarta.persistence.criteria.Predicate vaccine = cb.equal(source, "VACCINATION_PLAN");
            if (bucket == HandlerTaskQueueBucket.NOW) {
                return cb.or(vaccine, cb.isNull(root.get("dueAt")), cb.lessThan(root.get("dueAt"), tomorrow));
            }
            return cb.and(cb.or(cb.isNull(root.get("sourceType")), cb.notEqual(source, "VACCINATION_PLAN")),
                    cb.greaterThanOrEqualTo(root.get("dueAt"), tomorrow));
        };
    }

    private Specification<HandlerTask> vaccinationSource() {
        return (root, query, cb) -> cb.equal(cb.upper(root.get("sourceType")), "VACCINATION_PLAN");
    }

    /**
     * Future vaccine reminders are preloaded for the offline working set, but
     * remain hidden by the client until visibleFrom. This keeps reminders
     * available when a handler leaves Wi-Fi before the reminder date.
     */
    private Specification<HandlerTask> futureVaccinationWorkingSet(Long farmId, Long userId) {
        return (root, query, cb) -> cb.and(
                cb.equal(root.get("farmId"), farmId),
                root.get("status").in(TaskStatus.TODO, TaskStatus.IN_PROGRESS, TaskStatus.BLOCKED),
                cb.equal(cb.upper(root.get("sourceType")), "VACCINATION_PLAN"),
                cb.or(cb.equal(root.get("assignedHandlerId"), userId),
                        root.get("assignmentScope").in("BATCH_TEAM", "FARM_TEAM"))
        );
    }

    @Transactional(readOnly = true)
    public TaskResponse get(Long id, Long farmId) {
        return response(require(id, farmId));
    }

    @Transactional(readOnly = true)
    public TaskResponse get(Long id, Long farmId, Long userId, boolean manager) {
        HandlerTask task = require(id, farmId);
        if (!canViewTask(task, farmId, userId, manager)) {
            throw new NotFoundException("Task " + id + " not found");
        }
        return response(task);
    }

    @Transactional
    public TaskResponse update(Long id, Long farmId, Long managerId, UpdateTaskRequest request) {
        HandlerTask task = require(id, farmId);
        if (task.getBatchId() != null) batchService.requireWritableBatch(task.getBatchId(), farmId);
        if (request.title() != null && !request.title().isBlank()) task.setTitle(request.title().trim());
        if (request.instructions() != null) task.setInstructions(trim(request.instructions()));
        if (request.batchId() != null || request.incubationCycleId() != null
                || request.assignedHandlerId() != null || request.assignmentMode() != null) {
            Long batchId = request.batchId() != null ? request.batchId() : task.getBatchId();
            Long cycleId = request.incubationCycleId() != null ? request.incubationCycleId() : task.getIncubationCycleId();
            TaskAssignmentMode assignmentMode = request.assignmentMode() != null
                    ? request.assignmentMode()
                    : (task.getAssignedHandlerId() == null ? TaskAssignmentMode.ALL_HANDLERS : TaskAssignmentMode.SPECIFIC_HANDLER);
            Long handlerId = assignmentMode == TaskAssignmentMode.ALL_HANDLERS
                    ? null
                    : request.assignedHandlerId() != null ? request.assignedHandlerId() : task.getAssignedHandlerId();
            validateReferences(farmId, batchId, cycleId, handlerId, assignmentMode);
            task.setBatchId(batchId);
            task.setIncubationCycleId(cycleId);
            task.setAssignedHandlerId(assignmentMode == TaskAssignmentMode.SPECIFIC_HANDLER ? handlerId : null);
            task.setAssignmentScope(scopeFor(assignmentMode, batchId));
        }
        if (request.dueAt() != null) task.setDueAt(request.dueAt());
        if (request.priority() != null) task.setPriority(request.priority());
        if (request.completionNote() != null) task.setCompletionNote(trim(request.completionNote()));
        if ("VACCINATION_PLAN".equals(task.getSourceType()) && request.status() == TaskStatus.COMPLETED)
            throw new BadRequestException("Vaccination tasks are completed through the vaccination confirmation action");
        if (request.status() == TaskStatus.COMPLETED) {
            throw new BadRequestException("Managers monitor tasks; handlers complete them");
        }
        if (request.status() != null && request.status() != task.getStatus()) applyStatus(task, request.status(), managerId, task.getCompletionNote());
        task.setUpdatedAt(Instant.now(applicationClock));
        return response(taskRepository.save(task));
    }

    @Transactional
    public TaskResponse updateStatus(Long id, Long farmId, Long actorId, boolean manager,
                                     UpdateTaskStatusRequest request) {
        HandlerTask task = require(id, farmId);
        if (manager && request.status() == TaskStatus.COMPLETED) {
            throw new BadRequestException("Managers monitor tasks; handlers complete them");
        }
        if (!canViewTask(task, farmId, actorId, manager)) {
            throw new NotFoundException("Task " + id + " not found");
        }
        if (request.operationId() != null) {
            TaskStatusHistory existing = historyRepository.findByOperationId(request.operationId()).orElse(null);
            if (existing != null) {
                if (!id.equals(existing.getTaskId()) || existing.getStatus() != request.status()) {
                    throw new ConflictException("Task status operation has already been used differently");
                }
                return response(task);
            }
        }
        if (request.status() == TaskStatus.COMPLETED && task.getStatus() == TaskStatus.COMPLETED) {
            throw new ConflictException("Task was already completed by another handler");
        }
        if (task.getBatchId() != null) batchService.requireWritableBatch(task.getBatchId(), farmId);
        if ("VACCINATION_PLAN".equals(task.getSourceType()) && request.status() == TaskStatus.COMPLETED) {
            throw new BadRequestException("Vaccination tasks are completed through the vaccination confirmation action");
        }
        applyStatus(task, request.status(), actorId, request.note(), request.operationId(), request.occurredAt());
        task.setUpdatedAt(Instant.now(applicationClock));
        return response(taskRepository.save(task));
    }

    /** Internal fixture/system path; not exposed as a manager completion endpoint. */
    @Transactional
    public TaskResponse completeSystemTask(Long id, Long farmId, Long actorId, String note) {
        HandlerTask task = require(id, farmId);
        if (task.getBatchId() != null) batchService.requireWritableBatch(task.getBatchId(), farmId);
        if (task.getStatus() == TaskStatus.COMPLETED) return response(task);
        applyStatus(task, TaskStatus.COMPLETED, actorId, note, null, null);
        task.setUpdatedAt(Instant.now(applicationClock));
        return response(taskRepository.save(task));
    }

    @Transactional(readOnly = true)
    public HandlerTask require(Long id, Long farmId) {
        return taskRepository.findByIdAndFarmId(id, farmId)
                .orElseThrow(() -> new NotFoundException("Task " + id + " not found"));
    }

    private void validateReferences(Long farmId, Long batchId, Long cycleId, Long handlerId,
                                    TaskAssignmentMode assignmentMode) {
        if (batchId != null) batchService.requireWritableBatch(batchId, farmId);
        if (cycleId != null) incubationService.require(cycleId, farmId);
        if (handlerId != null) {
            User handler = userRepository.findById(handlerId)
                    .orElseThrow(() -> new BadRequestException("Assigned handler was not found"));
            if (handler.getRole() != Role.HANDLER || !farmId.equals(handler.getFarmId()))
                throw new BadRequestException("Assigned user is not a handler in this farm");
        }
        if (assignmentMode == TaskAssignmentMode.SPECIFIC_HANDLER && handlerId == null)
            throw new BadRequestException("Select a specific handler or choose All handlers");
        if (assignmentMode == TaskAssignmentMode.ALL_HANDLERS && handlerId != null)
            throw new BadRequestException("An All handlers task cannot have a specific handler");
    }

    private String scopeFor(TaskAssignmentMode assignmentMode, Long batchId) {
        if (assignmentMode == TaskAssignmentMode.SPECIFIC_HANDLER) return "HANDLER";
        return batchId == null ? "FARM_TEAM" : "BATCH_TEAM";
    }

    private boolean canViewTask(HandlerTask task, Long farmId, Long userId, boolean manager) {
        if (!java.util.Objects.equals(task.getFarmId(), farmId)) return false;
        if (manager) return true;
        if (java.util.Objects.equals(task.getAssignedHandlerId(), userId)) return true;
        boolean teamTask = "BATCH_TEAM".equals(task.getAssignmentScope()) || "FARM_TEAM".equals(task.getAssignmentScope());
        return teamTask && (task.getVisibleFrom() == null || !task.getVisibleFrom().isAfter(Instant.now(applicationClock)));
    }

    private void applyStatus(HandlerTask task, TaskStatus status, Long actorId, String note) {
        applyStatus(task, status, actorId, note, null, null);
    }

    private void applyStatus(HandlerTask task, TaskStatus status, Long actorId, String note,
                             UUID operationId, Instant occurredAt) {
        if (status == null) return;
        TaskStatus previousStatus = task.getStatus();
        task.setStatus(status);
        if (note != null) task.setCompletionNote(trim(note));
        if (status == TaskStatus.COMPLETED) { task.setCompletedAt(Instant.now(applicationClock)); task.setCompletedBy(actorId); }
        else { task.setCompletedAt(null); task.setCompletedBy(null); }
        recordStatus(task, actorId, previousStatus, status, note, operationId, occurredAt);
    }

    private void recordStatus(HandlerTask task, Long actorId, TaskStatus status, String note) {
        recordStatus(task, actorId, null, status, note, null, null);
    }

    private void recordStatus(HandlerTask task, Long actorId, TaskStatus previousStatus,
                              TaskStatus status, String note, UUID operationId, Instant occurredAt) {
        if (task.getId() == null) return;
        TaskStatusHistory history = new TaskStatusHistory();
        history.setTaskId(task.getId()); history.setChangedBy(actorId); history.setStatus(status);
        history.setPreviousStatus(previousStatus); history.setNote(trim(note));
        history.setOperationId(operationId); history.setOccurredAt(occurredAt);
        historyRepository.save(history);
    }

    private TaskResponse response(HandlerTask task) { return TaskResponse.from(task, Instant.now(applicationClock), applicationClock.getZone()); }
    private void requireFarm(Long farmId) { if (farmId == null) throw new BadRequestException("Join a farm first"); }
    private String trim(String value) { return value == null || value.isBlank() ? null : value.trim(); }
}
