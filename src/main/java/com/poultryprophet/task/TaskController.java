package com.poultryprophet.task;

import com.poultryprophet.security.CustomUserDetails;
import com.poultryprophet.common.PageResponse;
import com.poultryprophet.task.dto.CreateTaskRequest;
import com.poultryprophet.task.dto.TaskResponse;
import com.poultryprophet.task.dto.TaskSummaryResponse;
import com.poultryprophet.task.dto.HandlerTaskQueueSummary;
import com.poultryprophet.task.dto.UpdateTaskRequest;
import com.poultryprophet.task.dto.UpdateTaskStatusRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/tasks")
public class TaskController {
    private final TaskService service;
    public TaskController(TaskService service) { this.service = service; }

    @PostMapping
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseEntity<TaskResponse> create(@Valid @RequestBody CreateTaskRequest request,
                                                @AuthenticationPrincipal CustomUserDetails principal) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(principal.getFarmId(), principal.getId(), request));
    }

    @GetMapping
    public PageResponse<TaskResponse> list(@RequestParam(defaultValue = "0") int page,
                                           @RequestParam(defaultValue = "20") int size,
                                           @RequestParam(defaultValue = "OPEN") TaskView view,
                                           @RequestParam(defaultValue = "ALL") TaskDueBucket dueBucket,
                                           @RequestParam(required = false) Long batchId,
                                           @RequestParam(required = false) Boolean farmWide,
                                           @RequestParam(required = false) Long assignedHandlerId,
                                           @RequestParam(required = false) TaskPriority priority,
                                           @RequestParam(required = false) String sourceType,
                                           @RequestParam(required = false) String assignmentScope,
                                           @RequestParam(required = false) String search,
                                           @RequestParam(defaultValue = "false") boolean mine,
                                           @AuthenticationPrincipal CustomUserDetails principal) {
        boolean manager = principal.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_MANAGER"));
        return service.page(principal.getFarmId(), principal.getId(), manager,
                new TaskFilter(view, dueBucket, batchId, farmWide, assignedHandlerId, priority, sourceType, assignmentScope, search),
                page, size);
    }

    @GetMapping("/summary")
    public TaskSummaryResponse summary(@RequestParam(required = false) Long batchId,
                                       @RequestParam(required = false) Boolean farmWide,
                                       @RequestParam(required = false) Long assignedHandlerId,
                                       @RequestParam(required = false) TaskPriority priority,
                                       @RequestParam(required = false) String sourceType,
                                       @RequestParam(required = false) String assignmentScope,
                                       @RequestParam(required = false) String search,
                                       @AuthenticationPrincipal CustomUserDetails principal) {
        boolean manager = principal.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_MANAGER"));
        return service.summary(principal.getFarmId(), principal.getId(), manager,
                new TaskFilter(TaskView.ALL, TaskDueBucket.ALL, batchId, farmWide, assignedHandlerId, priority, sourceType, assignmentScope, search));
    }

    @GetMapping("/handler-queue")
    public PageResponse<TaskResponse> handlerQueue(@RequestParam(defaultValue = "NOW") HandlerTaskQueueBucket bucket,
                                                   @RequestParam(defaultValue = "0") int page,
                                                   @RequestParam(defaultValue = "10") int size,
                                                   @AuthenticationPrincipal CustomUserDetails principal) {
        return service.handlerQueue(principal.getFarmId(), principal.getId(), bucket, page, size);
    }

    @GetMapping("/handler-queue/summary")
    public HandlerTaskQueueSummary handlerQueueSummary(@AuthenticationPrincipal CustomUserDetails principal) {
        return service.handlerQueueSummary(principal.getFarmId(), principal.getId());
    }

    @GetMapping("/mine")
    public PageResponse<TaskResponse> mine(@RequestParam(defaultValue = "0") int page,
                                           @RequestParam(defaultValue = "20") int size,
                                           @RequestParam(defaultValue = "OPEN") TaskView view,
                                           @RequestParam(defaultValue = "ALL") TaskDueBucket dueBucket,
                                           @AuthenticationPrincipal CustomUserDetails principal) {
        return service.page(principal.getFarmId(), principal.getId(), false,
                new TaskFilter(view, dueBucket, null, null, null, null, null, null, null), page, size);
    }

    @GetMapping("/{id:\\d+}")
    public TaskResponse get(@PathVariable Long id, @AuthenticationPrincipal CustomUserDetails principal) {
        boolean manager = principal.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_MANAGER"));
        return service.get(id, principal.getFarmId(), principal.getId(), manager);
    }

    @PatchMapping("/{id:\\d+}")
    @PreAuthorize("hasRole('MANAGER')")
    public TaskResponse update(@PathVariable Long id, @RequestBody UpdateTaskRequest request,
                               @AuthenticationPrincipal CustomUserDetails principal) {
        return service.update(id, principal.getFarmId(), principal.getId(), request);
    }

    @PostMapping("/{id:\\d+}/status")
    public TaskResponse status(@PathVariable Long id, @Valid @RequestBody UpdateTaskStatusRequest request,
                               @AuthenticationPrincipal CustomUserDetails principal) {
        boolean manager = principal.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_MANAGER"));
        return service.updateStatus(id, principal.getFarmId(), principal.getId(), manager, request);
    }
}
