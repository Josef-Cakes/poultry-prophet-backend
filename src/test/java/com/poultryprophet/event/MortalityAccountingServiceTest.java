package com.poultryprophet.event;

import com.poultryprophet.batch.Batch;
import com.poultryprophet.batch.BatchRepository;
import com.poultryprophet.batch.BatchService;
import com.poultryprophet.common.BadRequestException;
import com.poultryprophet.common.DateValidationService;
import com.poultryprophet.event.dto.CreateBatchEventRequest;
import com.poultryprophet.record.DailyRecordRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class MortalityAccountingServiceTest {

    @Mock
    private BatchService batchService;

    @Mock
    private BatchRepository batchRepository;

    @Mock
    private BatchEventRepository eventRepository;

    @Mock
    private DailyRecordRepository recordRepository;

    @Mock
    private ApplicationEventPublisher publisher;

    @Mock
    private DateValidationService dateValidation;

    @InjectMocks
    private MortalityAccountingService service;

    @Test
    void deductsPopulationAndReturnsRemainingPopulationWithoutDailyRecord() {
        LocalDate date = LocalDate.now(ZoneId.of("Asia/Manila")).minusDays(1);
        when(dateValidation.resolve(date)).thenReturn(date);
        Batch batch = batch(55);
        when(batchService.requireBatchForUpdate(10L, 7L)).thenReturn(batch);
        when(eventRepository.save(any(BatchEvent.class))).thenAnswer(invocation -> {
            BatchEvent event = invocation.getArgument(0);
            event.setId(99L);
            return event;
        });
        when(eventRepository.sumAffectedCountByBatchIdAndEventDateAndEventType(
                10L, date, EventType.HEALTH_DEATH)).thenReturn(3L);
        when(recordRepository.findByBatchIdAndRecordDate(10L, date))
                .thenReturn(Optional.empty());

        MortalityAccountingService.MortalityAccountingResult result = service.record(
                10L, 7L, 21L,
                new CreateBatchEventRequest(date, EventType.MORTALITY,
                        "Unknown", null, 3, null, null));

        assertThat(batch.getCurrentPopulation()).isEqualTo(52);
        assertThat(result.remainingPopulation()).isEqualTo(52);
        assertThat(result.event().getAffectedCount()).isEqualTo(3);
        verify(batchRepository).save(batch);
        verify(publisher).publishEvent(any(MortalityRecordedEvent.class));
    }

    @Test
    void rejectsZeroDeathsBeforeSavingAnything() {
        LocalDate date = LocalDate.now(ZoneId.of("Asia/Manila")).minusDays(1);
        when(dateValidation.resolve(date)).thenReturn(date);
        Batch batch = batch(55);
        when(batchService.requireBatchForUpdate(10L, 7L)).thenReturn(batch);

        assertThatThrownBy(() -> service.record(
                10L, 7L, 21L,
                new CreateBatchEventRequest(date, EventType.MORTALITY,
                        "Unknown", null, 0, null, null)))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Population event count must be at least 1");

        verify(eventRepository).findByOperationId(any());
        verifyNoInteractions(batchRepository, publisher);
    }

    @Test
    void nonHealthPopulationLossChangesAliveCountWithoutHealthMortalityEvent() {
        LocalDate date = LocalDate.now(ZoneId.of("Asia/Manila")).minusDays(1);
        UUID operationId = UUID.randomUUID();
        when(dateValidation.resolve(date)).thenReturn(date);
        Batch batch = batch(55);
        when(batchService.requireBatchForUpdate(10L, 7L)).thenReturn(batch);
        when(eventRepository.findByOperationId(operationId)).thenReturn(Optional.empty());
        when(eventRepository.save(any(BatchEvent.class))).thenAnswer(invocation -> {
            BatchEvent event = invocation.getArgument(0);
            event.setId(100L);
            return event;
        });

        MortalityAccountingService.MortalityAccountingResult result = service.record(
                10L, 7L, 21L,
                new CreateBatchEventRequest(date, EventType.SUSPECTED_PREDATION,
                        "Suspected predation", null, 2, "Tracks found", null, operationId, null));

        assertThat(result.remainingPopulation()).isEqualTo(53);
        assertThat(result.event().getPopulationDelta()).isEqualTo(-2);
        assertThat(result.event().getPopulationAfter()).isEqualTo(53);
        verify(batchRepository).save(batch);
        verify(publisher, never()).publishEvent(any(MortalityRecordedEvent.class));
    }

    @Test
    void replaysAnOperationIdWithoutDeductingPopulationTwice() {
        LocalDate date = LocalDate.now(ZoneId.of("Asia/Manila")).minusDays(1);
        UUID operationId = UUID.randomUUID();
        when(dateValidation.resolve(date)).thenReturn(date);
        Batch batch = batch(55);
        when(batchService.requireBatchForUpdate(10L, 7L)).thenReturn(batch);
        BatchEvent existing = new BatchEvent();
        existing.setId(101L);
        existing.setBatchId(10L);
        existing.setPopulationAfter(54);
        existing.setPopulationDelta(-1);
        existing.setOperationId(operationId);
        existing.setAffectedCount(1);
        when(eventRepository.findByOperationId(operationId))
                .thenReturn(Optional.empty(), Optional.of(existing));
        when(eventRepository.save(any(BatchEvent.class))).thenAnswer(invocation -> {
            BatchEvent event = invocation.getArgument(0);
            event.setId(101L);
            return event;
        });

        service.record(10L, 7L, 21L,
                new CreateBatchEventRequest(date, EventType.ACCIDENTAL_DEATH,
                        "Accidental death", null, 1, null, null, operationId, null));
        MortalityAccountingService.MortalityAccountingResult retry = service.record(
                10L, 7L, 21L,
                new CreateBatchEventRequest(date, EventType.ACCIDENTAL_DEATH,
                        "Accidental death", null, 1, null, null, operationId, null));

        assertThat(batch.getCurrentPopulation()).isEqualTo(54);
        assertThat(retry.event().getId()).isEqualTo(101L);
        assertThat(retry.remainingPopulation()).isEqualTo(54);
        verify(eventRepository).save(any(BatchEvent.class));
    }

    private static Batch batch(int currentPopulation) {
        Batch batch = new Batch();
        batch.setId(10L);
        batch.setFarmId(7L);
        batch.setInitialPopulation(55);
        batch.setCurrentPopulation(currentPopulation);
        batch.setStartDate(LocalDate.now(ZoneId.of("Asia/Manila")).minusDays(10));
        return batch;
    }
}
