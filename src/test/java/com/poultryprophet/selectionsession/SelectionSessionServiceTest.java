package com.poultryprophet.selectionsession;

import com.poultryprophet.batch.Batch;
import com.poultryprophet.batch.BatchService;
import com.poultryprophet.common.BadRequestException;
import com.poultryprophet.event.BatchEventRepository;
import com.poultryprophet.population.PopulationProjectionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SelectionSessionServiceTest {
    private static final Long BATCH_ID = 7L;
    private static final Long FARM_ID = 3L;
    private static final Long REVIEWER_ID = 11L;

    @Mock private BatchSelectionSessionRepository repository;
    @Mock private BatchEventRepository eventRepository;
    @Mock private BatchService batchService;

    @Test
    void recordsAValidDraftWithAnIncompleteOutcomeDistribution() {
        LocalDate today = today();
        Batch batch = batch(20, today.minusDays(10));
        UUID operationId = UUID.randomUUID();
        CreateSelectionSessionRequest request = request(today, 10, 4, 2, 0, 0, operationId);
        when(batchService.requireBatch(BATCH_ID, FARM_ID)).thenReturn(batch);
        when(repository.findByOperationId(operationId)).thenReturn(Optional.empty());
        when(eventRepository.findByBatchIdAndEventDateBetweenOrderByEventDateAscCreatedAtAsc(
                BATCH_ID, batch.getStartDate(), today)).thenReturn(List.of());
        when(repository.save(any(BatchSelectionSession.class))).thenAnswer(invocation -> {
            BatchSelectionSession session = invocation.getArgument(0);
            session.setId(91L);
            return session;
        });

        SelectionSessionResponse response = service().create(BATCH_ID, FARM_ID, REVIEWER_ID, request, null);

        assertThat(response.status()).isEqualTo(SelectionSessionStatus.DRAFT);
        assertThat(response.evaluatedCount()).isEqualTo(10);
        assertThat(response.acceptedCount()).isEqualTo(4);
        assertThat(response.continueObservationCount()).isEqualTo(2);
    }

    @Test
    void rejectsOutcomeTotalsThatOverflowAnInteger() {
        LocalDate today = today();
        Batch batch = batch(Integer.MAX_VALUE, today.minusDays(1));
        UUID operationId = UUID.randomUUID();
        CreateSelectionSessionRequest request = request(today, Integer.MAX_VALUE,
                Integer.MAX_VALUE, Integer.MAX_VALUE, 0, 0, operationId);
        when(batchService.requireBatch(BATCH_ID, FARM_ID)).thenReturn(batch);
        when(repository.findByOperationId(operationId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().create(BATCH_ID, FARM_ID, REVIEWER_ID, request, null))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("cannot exceed evaluated birds");
        verify(repository, never()).save(any());
    }

    @Test
    void requiresEveryEvaluatedBirdToHaveAnOutcomeBeforeFinalizing() {
        LocalDate today = today();
        Batch batch = batch(20, today.minusDays(10));
        BatchSelectionSession session = session(today, 10, 4, 2, 0, 0);
        when(batchService.requireBatch(BATCH_ID, FARM_ID)).thenReturn(batch);
        when(repository.findByIdAndFarmIdAndBatchId(91L, FARM_ID, BATCH_ID)).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> service().finalize(BATCH_ID, FARM_ID, 91L))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("must equal evaluated birds");
        verify(repository, never()).save(any());
    }

    @Test
    void rejectsReusingAnOperationIdWithDifferentDetails() {
        LocalDate today = today();
        UUID operationId = UUID.randomUUID();
        BatchSelectionSession existing = session(today, 10, 4, 2, 2, 2);
        existing.setOperationId(operationId);
        when(batchService.requireBatch(BATCH_ID, FARM_ID)).thenReturn(batch(20, today.minusDays(10)));
        when(repository.findByOperationId(operationId)).thenReturn(Optional.of(existing));
        CreateSelectionSessionRequest changed = request(today, 10, 5, 1, 2, 2, operationId);

        assertThatThrownBy(() -> service().create(BATCH_ID, FARM_ID, REVIEWER_ID, changed, null))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("different selection details");
        verify(repository, never()).save(any());
    }

    @Test
    void validatesADraftUpdateAgainstItsStoredDateWhenDateIsOmitted() {
        LocalDate today = today();
        LocalDate storedDate = today.minusDays(5);
        Batch batch = batch(20, today.minusDays(20));
        BatchSelectionSession existing = session(storedDate, 10, 4, 2, 4, 0);
        when(batchService.requireBatch(BATCH_ID, FARM_ID)).thenReturn(batch);
        when(repository.findByIdAndFarmIdAndBatchId(91L, FARM_ID, BATCH_ID)).thenReturn(Optional.of(existing));
        when(eventRepository.findByBatchIdAndEventDateBetweenOrderByEventDateAscCreatedAtAsc(
                BATCH_ID, batch.getStartDate(), storedDate)).thenReturn(List.of());
        when(repository.save(existing)).thenReturn(existing);
        CreateSelectionSessionRequest update = request(null, 10, 4, 2, 4, 0, UUID.randomUUID());

        SelectionSessionResponse response = service().updateDraft(BATCH_ID, FARM_ID, 91L, update);

        assertThat(response.selectionDate()).isEqualTo(storedDate);
        verify(eventRepository).findByBatchIdAndEventDateBetweenOrderByEventDateAscCreatedAtAsc(
                BATCH_ID, batch.getStartDate(), storedDate);
    }

    private SelectionSessionService service() {
        return new SelectionSessionService(repository, eventRepository, batchService,
                new PopulationProjectionService(), "Asia/Manila");
    }

    private static Batch batch(int initialPopulation, LocalDate startDate) {
        Batch batch = new Batch();
        batch.setId(BATCH_ID);
        batch.setFarmId(FARM_ID);
        batch.setInitialPopulation(initialPopulation);
        batch.setCurrentPopulation(initialPopulation);
        batch.setStartDate(startDate);
        return batch;
    }

    private static BatchSelectionSession session(LocalDate date, int evaluated, int accepted,
                                                 int continued, int notAccepted, int other) {
        BatchSelectionSession session = new BatchSelectionSession();
        session.setId(91L);
        session.setFarmId(FARM_ID);
        session.setBatchId(BATCH_ID);
        session.setReviewerId(REVIEWER_ID);
        session.setSelectionDate(date);
        session.setEvaluatedCount(evaluated);
        session.setAcceptedCount(accepted);
        session.setContinueObservationCount(continued);
        session.setNotAcceptedCount(notAccepted);
        session.setOtherCount(other);
        session.setCriterionCodes(Set.of());
        session.setStatus(SelectionSessionStatus.DRAFT);
        return session;
    }

    private static CreateSelectionSessionRequest request(LocalDate date, int evaluated, int accepted,
                                                         int continued, int notAccepted, int other,
                                                         UUID operationId) {
        return new CreateSelectionSessionRequest(date, evaluated, accepted, continued, notAccepted,
                other, Set.of(), null, null, operationId);
    }

    private static LocalDate today() {
        return LocalDate.now(ZoneId.of("Asia/Manila"));
    }
}
