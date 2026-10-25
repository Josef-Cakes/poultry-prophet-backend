package com.poultryprophet.sexcomposition;

import com.poultryprophet.batch.Batch;
import com.poultryprophet.batch.BatchService;
import com.poultryprophet.common.ConflictException;
import com.poultryprophet.event.BatchEventRepository;
import com.poultryprophet.population.PopulationProjectionService;
import com.poultryprophet.sexcomposition.dto.CreateSexCompositionRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BatchSexCompositionServiceTest {
    private static final Long BATCH_ID = 7L;
    private static final Long FARM_ID = 3L;
    private static final Long USER_ID = 11L;
    private static final LocalDate HATCH_DATE = LocalDate.of(2026, 10, 1);
    private static final LocalDate OBSERVED_ON = LocalDate.of(2026, 10, 9);

    @Mock private BatchSexCompositionRepository repository;
    @Mock private BatchService batchService;
    @Mock private BatchEventRepository eventRepository;

    @Test
    void flushesTheSupersededCurrentRowBeforeInsertingAReplacement() {
        BatchSexComposition current = composition(42L, UUID.randomUUID(), OBSERVED_ON.minusDays(2), 60, 40, 0);
        UUID operationId = UUID.randomUUID();
        when(batchService.requireWritableBatchForUpdate(BATCH_ID, FARM_ID)).thenReturn(batch());
        when(repository.findByOperationId(operationId)).thenReturn(Optional.empty());
        when(repository.findByBatchIdAndFarmIdAndStatus(BATCH_ID, FARM_ID, "CURRENT"))
                .thenReturn(Optional.of(current));
        when(eventRepository.findByBatchIdAndEventDateBetweenOrderByEventDateAscCreatedAtAsc(
                BATCH_ID, HATCH_DATE, OBSERVED_ON)).thenReturn(List.of());
        when(repository.saveAndFlush(any(BatchSexComposition.class))).thenAnswer(invocation -> {
            BatchSexComposition saved = invocation.getArgument(0);
            if (saved.getId() == null) saved.setId(43L);
            return saved;
        });

        BatchSexCompositionService.RecordResult result = service().recordResult(
                BATCH_ID, FARM_ID, USER_ID,
                request(62, 38, 0, operationId, current.getId()));

        assertThat(result.alreadyApplied()).isFalse();
        ArgumentCaptor<BatchSexComposition> saved = ArgumentCaptor.forClass(BatchSexComposition.class);
        verify(repository, org.mockito.Mockito.times(2)).saveAndFlush(saved.capture());
        InOrder order = inOrder(repository);
        order.verify(repository).saveAndFlush(current);
        order.verify(repository).saveAndFlush(any(BatchSexComposition.class));
        assertThat(current.getStatus()).isEqualTo("SUPERSEDED");
        BatchSexComposition replacement = saved.getAllValues().get(1);
        assertThat(replacement.getStatus()).isEqualTo("CURRENT");
        assertThat(replacement.getSupersedesRecordId()).isEqualTo(42L);
    }

    @Test
    void rejectsAStaleExpectedCurrentRecordWithoutChangingTheDatabase() {
        BatchSexComposition current = composition(42L, UUID.randomUUID(), OBSERVED_ON, 60, 40, 0);
        UUID operationId = UUID.randomUUID();
        when(batchService.requireWritableBatchForUpdate(BATCH_ID, FARM_ID)).thenReturn(batch());
        when(repository.findByOperationId(operationId)).thenReturn(Optional.empty());
        when(repository.findByBatchIdAndFarmIdAndStatus(BATCH_ID, FARM_ID, "CURRENT"))
                .thenReturn(Optional.of(current));
        when(eventRepository.findByBatchIdAndEventDateBetweenOrderByEventDateAscCreatedAtAsc(
                BATCH_ID, HATCH_DATE, OBSERVED_ON)).thenReturn(List.of());

        assertThatThrownBy(() -> service().recordResult(BATCH_ID, FARM_ID, USER_ID,
                request(61, 39, 0, operationId, 41L)))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("another device");
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void treatsAMissingExpectedRecordAsStaleWhenAnotherCurrentRecordAlreadyExists() {
        BatchSexComposition current = composition(42L, UUID.randomUUID(), OBSERVED_ON, 60, 40, 0);
        UUID operationId = UUID.randomUUID();
        when(batchService.requireWritableBatchForUpdate(BATCH_ID, FARM_ID)).thenReturn(batch());
        when(repository.findByOperationId(operationId)).thenReturn(Optional.empty());
        when(repository.findByBatchIdAndFarmIdAndStatus(BATCH_ID, FARM_ID, "CURRENT"))
                .thenReturn(Optional.of(current));
        when(eventRepository.findByBatchIdAndEventDateBetweenOrderByEventDateAscCreatedAtAsc(
                BATCH_ID, HATCH_DATE, OBSERVED_ON)).thenReturn(List.of());

        assertThatThrownBy(() -> service().recordResult(BATCH_ID, FARM_ID, USER_ID,
                request(61, 39, 0, operationId, null)))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("another device");
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void returnsAlreadyAppliedForAnIdenticalOperationReplay() {
        UUID operationId = UUID.randomUUID();
        BatchSexComposition existing = composition(42L, operationId, OBSERVED_ON, 60, 40, 0);
        when(batchService.requireWritableBatchForUpdate(BATCH_ID, FARM_ID)).thenReturn(batch());
        when(repository.findByOperationId(operationId)).thenReturn(Optional.of(existing));

        BatchSexCompositionService.RecordResult result = service().recordResult(
                BATCH_ID, FARM_ID, USER_ID, request(60, 40, 0, operationId, 42L));

        assertThat(result.alreadyApplied()).isTrue();
        assertThat(result.response().id()).isEqualTo(42L);
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void rejectsReusingAnOperationIdWithDifferentCounts() {
        UUID operationId = UUID.randomUUID();
        BatchSexComposition existing = composition(42L, operationId, OBSERVED_ON, 60, 40, 0);
        when(batchService.requireWritableBatchForUpdate(BATCH_ID, FARM_ID)).thenReturn(batch());
        when(repository.findByOperationId(operationId)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service().recordResult(BATCH_ID, FARM_ID, USER_ID,
                request(59, 41, 0, operationId, 42L)))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("different sex-composition data");
        verify(repository, never()).saveAndFlush(any());
    }

    private BatchSexCompositionService service() {
        return new BatchSexCompositionService(repository, batchService, eventRepository,
                new PopulationProjectionService(), new SexCompositionProjectionService(repository, eventRepository));
    }

    private static Batch batch() {
        Batch batch = new Batch();
        batch.setId(BATCH_ID);
        batch.setFarmId(FARM_ID);
        batch.setInitialPopulation(100);
        batch.setCurrentPopulation(100);
        batch.setStartDate(HATCH_DATE);
        batch.setHatchDate(HATCH_DATE);
        return batch;
    }

    private static BatchSexComposition composition(Long id, UUID operationId, LocalDate observedOn,
                                                   int male, int female, int unclassified) {
        BatchSexComposition composition = new BatchSexComposition();
        composition.setId(id);
        composition.setFarmId(FARM_ID);
        composition.setBatchId(BATCH_ID);
        composition.setObservedOn(observedOn);
        composition.setPopulationAsOfObservation(male + female + unclassified);
        composition.setMaleCount(male);
        composition.setFemaleCount(female);
        composition.setUnclassifiedCount(unclassified);
        composition.setRecordedBy(USER_ID);
        composition.setOperationId(operationId);
        composition.setStatus("CURRENT");
        return composition;
    }

    private static CreateSexCompositionRequest request(int male, int female, int unclassified,
                                                       UUID operationId, Long expectedCurrentId) {
        return new CreateSexCompositionRequest(OBSERVED_ON, male, female, unclassified,
                null, null, operationId, expectedCurrentId);
    }
}
