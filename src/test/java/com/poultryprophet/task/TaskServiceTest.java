package com.poultryprophet.task;

import com.poultryprophet.batch.BatchService;
import com.poultryprophet.common.BadRequestException;
import com.poultryprophet.incubation.IncubationService;
import com.poultryprophet.task.dto.CreateTaskRequest;
import com.poultryprophet.task.dto.TaskResponse;
import com.poultryprophet.task.dto.HandlerTaskQueueSummary;
import com.poultryprophet.task.dto.UpdateTaskStatusRequest;
import com.poultryprophet.user.Role;
import com.poultryprophet.user.User;
import com.poultryprophet.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;
import java.util.List;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class TaskServiceTest {
    @Mock HandlerTaskRepository taskRepository;
    @Mock TaskStatusHistoryRepository historyRepository;
    @Mock BatchService batchService;
    @Mock IncubationService incubationService;
    @Mock UserRepository userRepository;

    private TaskService service;

    @BeforeEach
    void setUp() {
        service = new TaskService(taskRepository, historyRepository, batchService, incubationService,
                userRepository, Clock.fixed(Instant.parse("2026-10-23T00:00:00Z"), ZoneId.of("Asia/Manila")));
    }

    @Test
    void createsFarmWideTaskWithoutAHandler() {
        when(taskRepository.save(any(HandlerTask.class))).thenAnswer(invocation -> invocation.getArgument(0));
        TaskResponse saved = service.create(1L, 10L,
                new CreateTaskRequest("Check waterers", null, null, null, null, null,
                        TaskPriority.NORMAL, TaskAssignmentMode.ALL_HANDLERS));

        assertEquals("FARM_TEAM", saved.assignmentScope());
        assertNull(saved.assignedHandlerId());
    }

    @Test
    void createsBatchTeamTaskWithoutAHandler() {
        when(taskRepository.save(any(HandlerTask.class))).thenAnswer(invocation -> invocation.getArgument(0));
        TaskResponse saved = service.create(1L, 10L,
                new CreateTaskRequest("Inspect batch", null, 22L, null, null, null,
                        TaskPriority.NORMAL, TaskAssignmentMode.ALL_HANDLERS));

        assertEquals("BATCH_TEAM", saved.assignmentScope());
        assertNull(saved.assignedHandlerId());
    }

    @Test
    void requiresHandlerForSpecificAssignment() {
        assertThrows(BadRequestException.class, () -> service.create(1L, 10L,
                new CreateTaskRequest("Private task", null, null, null, null, null,
                        TaskPriority.NORMAL, TaskAssignmentMode.SPECIFIC_HANDLER)));
    }

    @Test
    void preventsManagerFromCompletingThroughNormalStatusRoute() {
        HandlerTask task = new HandlerTask();
        task.setId(7L);
        task.setFarmId(1L);
        task.setAssignedHandlerId(22L);
        task.setAssignmentScope("HANDLER");
        task.setStatus(TaskStatus.TODO);
        when(taskRepository.findByIdAndFarmId(7L, 1L)).thenReturn(Optional.of(task));

        assertThrows(BadRequestException.class, () -> service.updateStatus(7L, 1L, 10L, true,
                new UpdateTaskStatusRequest(TaskStatus.COMPLETED, null)));
    }

    @Test
    void createsHandlerQueueSummaryFromSeparateNowAndUpcomingCounts() {
        HandlerTaskQueueSummary expected = new HandlerTaskQueueSummary(2, 3, 4, 1);
        when(taskRepository.count(org.mockito.ArgumentMatchers.<Specification<HandlerTask>>any())).thenReturn(2L, 3L, 4L, 1L);

        HandlerTaskQueueSummary actual = service.handlerQueueSummary(1L, 22L);

        assertEquals(expected, actual);
        verify(taskRepository, org.mockito.Mockito.times(4)).count(org.mockito.ArgumentMatchers.<Specification<HandlerTask>>any());
    }

    @Test
    void paginatedHandlerQueueDoesNotUseUnsupportedSpringDataNullPrecedence() {
        when(taskRepository.findAll(org.mockito.ArgumentMatchers.<Specification<HandlerTask>>any(),
                org.mockito.ArgumentMatchers.any(Pageable.class))).thenReturn(Page.empty());

        service.handlerQueue(1L, 22L, HandlerTaskQueueBucket.NOW, 0, 10);

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(taskRepository).findAll(org.mockito.ArgumentMatchers.<Specification<HandlerTask>>any(), pageable.capture());
        assertTrue(pageable.getValue().getSort().isUnsorted(),
                "Task ordering is applied through Criteria CASE expressions, not Sort.nullsLast()");
    }
}
