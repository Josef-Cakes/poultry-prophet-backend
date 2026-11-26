package com.poultryprophet.vaccination;

import com.poultryprophet.batch.BatchService;
import com.poultryprophet.common.ConflictException;
import com.poultryprophet.input.FarmInputService;
import com.poultryprophet.inventory.FarmProductRepository;
import com.poultryprophet.task.HandlerTaskRepository;
import com.poultryprophet.task.TaskStatusHistoryRepository;
import com.poultryprophet.user.UserRepository;
import com.poultryprophet.vaccination.dto.SkipVaccinationRequest;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class VaccinationArchivedBatchTest {
    @Test void skippingAnArchivedBatchCannotChangePlanOrTask() {
        var plans = mock(BatchVaccinationPlanItemRepository.class);
        var tasks = mock(HandlerTaskRepository.class);
        var batches = mock(BatchService.class);
        var plan = new BatchVaccinationPlanItem();
        plan.setId(10L); plan.setFarmId(3L); plan.setBatchId(4L); plan.setTaskId(15L);
        plan.setDueDate(LocalDate.of(2026, 11, 26));
        when(plans.findByIdAndFarmIdForUpdate(10L, 3L)).thenReturn(Optional.of(plan));
        lenient().when(plans.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(batches.requireWritableBatch(4L, 3L)).thenThrow(new ConflictException("Archived batch is read-only"));
        var service = new VaccinationService(mock(VaccinationProgramRepository.class), mock(VaccinationProgramItemRepository.class),
                plans, tasks, mock(TaskStatusHistoryRepository.class), batches, mock(FarmInputService.class),
                mock(UserRepository.class), mock(FarmProductRepository.class));

        assertThatThrownBy(() -> service.skip(10L, 3L, 7L, new SkipVaccinationRequest("Not given")))
                .isInstanceOf(ConflictException.class).hasMessageContaining("read-only");
        assertThat(plan.getStatus()).isEqualTo("SCHEDULED");
        verify(plans, never()).save(any());
        verifyNoInteractions(tasks);
    }
}
