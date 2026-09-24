package com.poultryprophet.task;

import com.poultryprophet.security.CustomUserDetails;
import com.poultryprophet.task.dto.CreateTaskRequest;
import com.poultryprophet.task.dto.TaskResponse;
import com.poultryprophet.task.dto.UpdateTaskRequest;
import com.poultryprophet.task.dto.UpdateTaskStatusRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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
    public List<TaskResponse> list(@RequestParam(defaultValue = "false") boolean mine,
                                   @AuthenticationPrincipal CustomUserDetails principal) {
        return service.list(principal.getFarmId(), principal.getId(), mine);
    }

    @GetMapping("/mine")
    public List<TaskResponse> mine(@AuthenticationPrincipal CustomUserDetails principal) {
        return service.list(principal.getFarmId(), principal.getId(), true);
    }

    @GetMapping("/{id}")
    public TaskResponse get(@PathVariable Long id, @AuthenticationPrincipal CustomUserDetails principal) {
        return service.get(id, principal.getFarmId());
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasRole('MANAGER')")
    public TaskResponse update(@PathVariable Long id, @RequestBody UpdateTaskRequest request,
                               @AuthenticationPrincipal CustomUserDetails principal) {
        return service.update(id, principal.getFarmId(), principal.getId(), request);
    }

    @PostMapping("/{id}/status")
    public TaskResponse status(@PathVariable Long id, @Valid @RequestBody UpdateTaskStatusRequest request,
                               @AuthenticationPrincipal CustomUserDetails principal) {
        boolean manager = principal.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_MANAGER"));
        return service.updateStatus(id, principal.getFarmId(), principal.getId(), manager, request);
    }
}
