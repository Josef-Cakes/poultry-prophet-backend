package com.poultryprophet.batch;

import com.poultryprophet.batch.dto.BatchResponse;
import com.poultryprophet.batch.dto.CreateBatchRequest;
import com.poultryprophet.common.BadRequestException;
import com.poultryprophet.user.Role;
import com.poultryprophet.user.User;
import com.poultryprophet.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BatchServiceTest {

    @Mock
    private BatchRepository batchRepository;

    @Mock
    private LifecycleStageRepository stageRepository;

    @Mock
    private BatchHandlerAssignmentRepository assignmentRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private BatchService batchService;

    @Test
    void rejectsCreationUntilHandlerJoinsAFarm() {
        CreateBatchRequest request = request();

        assertThatThrownBy(() -> batchService.create(request, null))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Join a farm before creating a batch");

        verifyNoInteractions(batchRepository, stageRepository, assignmentRepository, userRepository);
    }

    @Test
    void createsBatchForAnyAuthenticatedFarmMember() {
        LifecycleStage stage = new LifecycleStage("brooding", 0);
        stage.setId(1L);
        when(batchRepository.existsByFarmIdAndNameIgnoreCase(7L, "September flock"))
                .thenReturn(false);
        when(stageRepository.findById(1L)).thenReturn(Optional.of(stage));
        when(batchRepository.save(any(Batch.class))).thenAnswer(invocation -> {
            Batch batch = invocation.getArgument(0);
            batch.setId(11L);
            return batch;
        });

        BatchResponse response = batchService.create(request(), 7L);

        assertThat(response.id()).isEqualTo(11L);
        assertThat(response.farmId()).isEqualTo(7L);
        assertThat(response.name()).isEqualTo("September flock");
        verify(batchRepository).save(any(Batch.class));
    }

    @Test
    void rejectsAssigningAnUnassignedHandlerWithoutThrowingNullPointerException() {
        LifecycleStage stage = new LifecycleStage("brooding", 0);
        stage.setId(1L);
        User unassignedHandler = new User();
        unassignedHandler.setId(33L);
        unassignedHandler.setRole(Role.HANDLER);
        unassignedHandler.setFarmId(null);
        when(batchRepository.existsByFarmIdAndNameIgnoreCase(7L, "September flock"))
                .thenReturn(false);
        when(stageRepository.findById(1L)).thenReturn(Optional.of(stage));
        when(userRepository.findById(33L)).thenReturn(Optional.of(unassignedHandler));

        CreateBatchRequest request = new CreateBatchRequest(
                "September flock",
                25,
                LocalDate.of(2026, 9, 12),
                1L,
                null,
                null,
                List.of(33L));

        assertThatThrownBy(() -> batchService.create(request, 7L))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Handler 33 belongs to a different farm");
    }

    private static CreateBatchRequest request() {
        return new CreateBatchRequest(
                "September flock",
                25,
                LocalDate.of(2026, 9, 12),
                1L,
                null,
                null,
                List.of());
    }
}
