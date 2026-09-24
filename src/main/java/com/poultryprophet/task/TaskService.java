package com.poultryprophet.task;

import com.poultryprophet.batch.BatchService;
import com.poultryprophet.common.BadRequestException;
import com.poultryprophet.common.NotFoundException;
import com.poultryprophet.incubation.IncubationService;
import com.poultryprophet.task.dto.CreateTaskRequest;
import com.poultryprophet.task.dto.TaskResponse;
import com.poultryprophet.task.dto.UpdateTaskRequest;
import com.poultryprophet.task.dto.UpdateTaskStatusRequest;
import com.poultryprophet.user.Role;
import com.poultryprophet.user.User;
import com.poultryprophet.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class TaskService {
    private final HandlerTaskRepository taskRepository;
    private final TaskStatusHistoryRepository historyRepository;
    private final BatchService batchService;
    private final IncubationService incubationService;
    private final UserRepository userRepository;

    public TaskService(HandlerTaskRepository taskRepository, TaskStatusHistoryRepository historyRepository,
                       BatchService batchService, IncubationService incubationService,
                       UserRepository userRepository) {
        this.taskRepository = taskRepository;
        this.historyRepository = historyRepository;
        this.batchService = batchService;
        this.incubationService = incubationService;
        this.userRepository = userRepository;
    }

    @Transactional
    public TaskResponse create(Long farmId, Long managerId, CreateTaskRequest request) {
        requireFarm(farmId);
        validateReferences(farmId, request.batchId(), request.incubationCycleId(), request.assignedHandlerId());
        HandlerTask task = new HandlerTask();
        task.setFarmId(farmId);
        task.setBatchId(request.batchId());
        task.setIncubationCycleId(request.incubationCycleId());
        task.setTitle(request.title().trim());
        task.setInstructions(trim(request.instructions()));
        task.setAssignedHandlerId(request.assignedHandlerId());
        task.setAssignedManagerId(managerId);
        task.setDueAt(request.dueAt());
        task.setPriority(request.priority() == null ? TaskPriority.NORMAL : request.priority());
        task.setUpdatedAt(Instant.now());
        HandlerTask saved = taskRepository.save(task);
        recordStatus(saved, managerId, TaskStatus.TODO, null);
        return response(saved);
    }

    @Transactional(readOnly = true)
    public List<TaskResponse> list(Long farmId, Long handlerId, boolean mine) {
        requireFarm(farmId);
        List<HandlerTask> tasks = mine
                ? taskRepository.findByFarmIdAndAssignedHandlerIdOrderByDueAtAscCreatedAtDesc(farmId, handlerId)
                : taskRepository.findByFarmIdOrderByDueAtAscCreatedAtDesc(farmId);
        return tasks.stream().map(this::response).toList();
    }

    @Transactional(readOnly = true)
    public TaskResponse get(Long id, Long farmId) {
        return response(require(id, farmId));
    }

    @Transactional
    public TaskResponse update(Long id, Long farmId, Long managerId, UpdateTaskRequest request) {
        HandlerTask task = require(id, farmId);
        if (request.title() != null && !request.title().isBlank()) task.setTitle(request.title().trim());
        if (request.instructions() != null) task.setInstructions(trim(request.instructions()));
        if (request.batchId() != null || request.incubationCycleId() != null || request.assignedHandlerId() != null) {
            Long batchId = request.batchId() != null ? request.batchId() : task.getBatchId();
            Long cycleId = request.incubationCycleId() != null ? request.incubationCycleId() : task.getIncubationCycleId();
            Long handlerId = request.assignedHandlerId() != null ? request.assignedHandlerId() : task.getAssignedHandlerId();
            validateReferences(farmId, batchId, cycleId, handlerId);
            task.setBatchId(batchId); task.setIncubationCycleId(cycleId); task.setAssignedHandlerId(handlerId);
        }
        if (request.dueAt() != null) task.setDueAt(request.dueAt());
        if (request.priority() != null) task.setPriority(request.priority());
        if (request.completionNote() != null) task.setCompletionNote(trim(request.completionNote()));
        if (request.status() != null && request.status() != task.getStatus()) applyStatus(task, request.status(), managerId, task.getCompletionNote());
        task.setUpdatedAt(Instant.now());
        return response(taskRepository.save(task));
    }

    @Transactional
    public TaskResponse updateStatus(Long id, Long farmId, Long actorId, boolean manager,
                                     UpdateTaskStatusRequest request) {
        HandlerTask task = require(id, farmId);
        if (!manager && !actorId.equals(task.getAssignedHandlerId())) {
            throw new BadRequestException("Only the assigned handler can update this task");
        }
        applyStatus(task, request.status(), actorId, request.note());
        task.setUpdatedAt(Instant.now());
        return response(taskRepository.save(task));
    }

    @Transactional(readOnly = true)
    public HandlerTask require(Long id, Long farmId) {
        return taskRepository.findByIdAndFarmId(id, farmId)
                .orElseThrow(() -> new NotFoundException("Task " + id + " not found"));
    }

    private void validateReferences(Long farmId, Long batchId, Long cycleId, Long handlerId) {
        if (batchId != null) batchService.requireBatch(batchId, farmId);
        if (cycleId != null) incubationService.require(cycleId, farmId);
        if (handlerId != null) {
            User handler = userRepository.findById(handlerId)
                    .orElseThrow(() -> new BadRequestException("Assigned handler was not found"));
            if (handler.getRole() != Role.HANDLER || !farmId.equals(handler.getFarmId()))
                throw new BadRequestException("Assigned user is not a handler in this farm");
        }
        if (batchId == null && cycleId == null && handlerId == null)
            throw new BadRequestException("A task needs a batch, incubation cycle, or assigned handler");
    }

    private void applyStatus(HandlerTask task, TaskStatus status, Long actorId, String note) {
        if (status == null) return;
        task.setStatus(status);
        if (note != null) task.setCompletionNote(trim(note));
        if (status == TaskStatus.COMPLETED) task.setCompletedAt(Instant.now());
        else task.setCompletedAt(null);
        recordStatus(task, actorId, status, note);
    }

    private void recordStatus(HandlerTask task, Long actorId, TaskStatus status, String note) {
        if (task.getId() == null) return;
        TaskStatusHistory history = new TaskStatusHistory();
        history.setTaskId(task.getId()); history.setChangedBy(actorId); history.setStatus(status); history.setNote(trim(note));
        historyRepository.save(history);
    }

    private TaskResponse response(HandlerTask task) { return TaskResponse.from(task, Instant.now()); }
    private void requireFarm(Long farmId) { if (farmId == null) throw new BadRequestException("Join a farm first"); }
    private String trim(String value) { return value == null || value.isBlank() ? null : value.trim(); }
}
