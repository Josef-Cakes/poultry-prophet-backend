package com.poultryprophet.event;

import com.poultryprophet.batch.Batch;
import com.poultryprophet.batch.BatchRepository;
import com.poultryprophet.batch.BatchService;
import com.poultryprophet.common.BadRequestException;
import com.poultryprophet.common.ConflictException;
import com.poultryprophet.common.DateValidationService;
import com.poultryprophet.event.dto.CreateBatchEventRequest;
import com.poultryprophet.population.PopulationProjectionService;
import com.poultryprophet.record.DailyRecordRepository;
import com.poultryprophet.sexcomposition.SexCompositionProjectionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDate;
import java.util.List;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

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

    @Spy
    private PopulationProjectionService populationProjectionService = new PopulationProjectionService();

    @Mock
    private SexCompositionProjectionService sexCompositionProjectionService;

    @InjectMocks
    private MortalityAccountingService service;

    @Test
    void rejectsBlankSignsEvenWhenAdditionalNotesFollowWithoutChangingPopulation() {
        LocalDate date = LocalDate.now(ZoneId.of("Asia/Manila")).minusDays(1);
        when(dateValidation.resolve(date)).thenReturn(date);
        Batch batch = batch(55);
        when(batchService.requireBatchForUpdate(10L, 7L)).thenReturn(batch);

        assertThatThrownBy(() -> service.record(10L, 7L, 21L,
                new CreateBatchEventRequest(date, EventType.HEALTH_DEATH,
                        "Health-related death", null, 1, "Signs noticed:  \nAdditional notes", null)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("signs");

        assertThat(batch.getCurrentPopulation()).isEqualTo(55);
        verify(eventRepository, never()).save(any());
        verifyNoInteractions(batchRepository, publisher);
    }

    static Stream<Arguments> validHealthObservations() {
        return Stream.of(
                Arguments.of("SIGNS", "trouble breathing", "Signs noticed: trouble breathing"),
                Arguments.of("SIGNS", "coughing\nnot eating", "Signs noticed: coughing\nnot eating\nAdditional note"),
                Arguments.of("NO_SIGNS", null, "No signs noticed."),
                Arguments.of("UNKNOWN", null, "Signs not observed / unknown."),
                Arguments.of(null, null, "Signs noticed: weak and not eating"),
                Arguments.of(null, null, "No signs noticed."),
                Arguments.of(null, null, "Signs not observed / unknown."),
                Arguments.of(null, null, "Older client: weak and not eating")
        );
    }

    @ParameterizedTest
    @MethodSource("validHealthObservations")
    void savesValidStructuredAndLegacyObservations(String observation, String signs, String details) {
        LocalDate date = LocalDate.now(ZoneId.of("Asia/Manila")).minusDays(1);
        when(dateValidation.resolve(date)).thenReturn(date);
        Batch batch = batch(55);
        when(batchService.requireBatchForUpdate(10L, 7L)).thenReturn(batch);
        when(eventRepository.save(any(BatchEvent.class))).thenAnswer(invocation -> {
            BatchEvent event = invocation.getArgument(0);
            event.setId(99L);
            return event;
        });
        MortalityAccountingService.MortalityAccountingResult result = service.record(10L, 7L, 21L,
                healthDeath(date, details, observation, signs));
        assertThat(result.event().getDetails()).isEqualTo(details);
        assertThat(result.remainingPopulation()).isEqualTo(54);
        assertThat(batch.getCurrentPopulation()).isEqualTo(54);
    }

    static Stream<Arguments> invalidHealthObservations() {
        return Stream.of(
                Arguments.of("SIGNS", "   ", "Signs noticed: "),
                Arguments.of("SIGNS", "\nweak", "Signs noticed: \nweak"),
                Arguments.of("SIGNS", "cough", "No signs noticed."),
                Arguments.of("NO_SIGNS", "cough", "No signs noticed."),
                Arguments.of("NO_SIGNS", null, "Signs noticed: coughing"),
                Arguments.of("UNKNOWN", null, "No signs noticed."),
                Arguments.of("INVALID", null, "Unknown"),
                Arguments.of(null, null, null),
                Arguments.of(null, null, "Signs noticed:   "),
                Arguments.of(null, null, "Signs noticed:\nOther details")
        );
    }

    @ParameterizedTest
    @MethodSource("invalidHealthObservations")
    void rejectsInvalidObservationsBeforeChangingPopulation(String observation, String signs, String details) {
        LocalDate date = LocalDate.now(ZoneId.of("Asia/Manila")).minusDays(1);
        when(dateValidation.resolve(date)).thenReturn(date);
        Batch batch = batch(55);
        when(batchService.requireBatchForUpdate(10L, 7L)).thenReturn(batch);

        assertThatThrownBy(() -> service.record(10L, 7L, 21L,
                healthDeath(date, details, observation, signs))).isInstanceOf(BadRequestException.class);
        assertThat(batch.getCurrentPopulation()).isEqualTo(55);
        verify(eventRepository, never()).save(any());
        verifyNoInteractions(batchRepository, publisher);
    }

    @Test
    void replaysPreviouslySavedLegacyDeathWithoutApplyingNewValidationOrDeductingAgain() {
        LocalDate date = LocalDate.now(ZoneId.of("Asia/Manila")).minusDays(1);
        UUID operationId = UUID.randomUUID();
        when(dateValidation.resolve(date)).thenReturn(date);
        Batch batch = batch(55);
        when(batchService.requireBatchForUpdate(10L, 7L)).thenReturn(batch);
        BatchEvent existing = new BatchEvent();
        existing.setId(98L);
        existing.setBatchId(10L);
        existing.setOperationId(operationId);
        existing.setEventDate(date);
        existing.setEventType(EventType.HEALTH_DEATH);
        existing.setTitle("Health-related death");
        existing.setAffectedCount(1);
        existing.setPopulationDelta(-1);
        existing.setDetails("Signs noticed: ");
        when(eventRepository.findByOperationId(operationId)).thenReturn(Optional.of(existing));
        var retry = new CreateBatchEventRequest(date, EventType.HEALTH_DEATH,
                "Health-related death", null, 1, "Signs noticed: ", null, operationId, null);

        assertThat(service.record(10L, 7L, 21L, retry).event().getId()).isEqualTo(98L);
        assertThat(batch.getCurrentPopulation()).isEqualTo(55);
        verify(eventRepository, never()).save(any());
        verifyNoInteractions(batchRepository, publisher);
    }

    @Test
    void structuredReplayCannotReplaceMissingDetailsOnAnExistingLegacyDeath() {
        LocalDate date = LocalDate.now(ZoneId.of("Asia/Manila")).minusDays(1);
        UUID operationId = UUID.randomUUID();
        when(dateValidation.resolve(date)).thenReturn(date);
        Batch batch = batch(55);
        when(batchService.requireBatchForUpdate(10L, 7L)).thenReturn(batch);
        BatchEvent existing = new BatchEvent();
        existing.setId(98L);
        existing.setBatchId(10L);
        existing.setOperationId(operationId);
        existing.setEventDate(date);
        existing.setEventType(EventType.HEALTH_DEATH);
        existing.setTitle("Health-related death");
        existing.setAffectedCount(1);
        existing.setPopulationDelta(-1);
        when(eventRepository.findByOperationId(operationId)).thenReturn(Optional.of(existing));
        var changed = new CreateBatchEventRequest(date, EventType.HEALTH_DEATH,
                "Health-related death", null, 1, "Signs noticed: coughing", null, operationId,
                null, null, null, null, "SIGNS", "coughing");

        assertThatThrownBy(() -> service.record(10L, 7L, 21L, changed)).isInstanceOf(ConflictException.class);
        assertThat(batch.getCurrentPopulation()).isEqualTo(55);
        verify(eventRepository, never()).save(any());
        verifyNoInteractions(batchRepository, publisher);
    }

    private static CreateBatchEventRequest healthDeath(LocalDate date, String details, String observation, String signs) {
        return new CreateBatchEventRequest(date, EventType.HEALTH_DEATH, "Health-related death", null,
                1, details, null, UUID.randomUUID(), null, null, null, null, observation, signs);
    }

    @Test
    void deductsPopulationAndReturnsRemainingPopulationWithoutDailyRecord() {
        LocalDate date = LocalDate.now(ZoneId.of("Asia/Manila")).minusDays(1);
        when(dateValidation.resolve(date)).thenReturn(date);
        when(dateValidation.today()).thenReturn(LocalDate.now(ZoneId.of("Asia/Manila")));
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
                        "Unknown", null, 3, "Signs not observed / unknown.", null));

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
        assertThat(batch.getCurrentPopulation()).isEqualTo(54);
        // A later event may have changed the current population since the original write.
        batch.setCurrentPopulation(53);
        MortalityAccountingService.MortalityAccountingResult retry = service.record(
                10L, 7L, 21L,
                new CreateBatchEventRequest(date, EventType.ACCIDENTAL_DEATH,
                        "Accidental death", null, 1, null, null, operationId, null));

        assertThat(batch.getCurrentPopulation()).isEqualTo(53);
        assertThat(retry.event().getId()).isEqualTo(101L);
        assertThat(retry.remainingPopulation()).isEqualTo(53);
        verify(eventRepository).save(any(BatchEvent.class));
    }

    @Test
    void persistsSignedSexDeltasWithThePopulationEvent() {
        LocalDate date = LocalDate.now(ZoneId.of("Asia/Manila")).minusDays(1);
        UUID operationId = UUID.randomUUID();
        when(dateValidation.resolve(date)).thenReturn(date);
        when(dateValidation.today()).thenReturn(LocalDate.now(ZoneId.of("Asia/Manila")));
        Batch batch = batch(55);
        when(batchService.requireBatchForUpdate(10L, 7L)).thenReturn(batch);
        when(sexCompositionProjectionService.hasBaseline(10L, 7L, date)).thenReturn(true);
        when(eventRepository.findByOperationId(operationId)).thenReturn(Optional.empty());
        when(eventRepository.save(any(BatchEvent.class))).thenAnswer(invocation -> {
            BatchEvent event = invocation.getArgument(0);
            event.setId(102L);
            return event;
        });

        MortalityAccountingService.MortalityAccountingResult result = service.record(
                10L, 7L, 21L,
                new CreateBatchEventRequest(date, EventType.CULLING, "Culling", null, 1,
                        null, null, operationId, null,
                        new CreateBatchEventRequest.SexAllocation(-1, 0, 0)));

        assertThat(result.event().getMaleDelta()).isEqualTo(-1);
        assertThat(result.event().getFemaleDelta()).isEqualTo(0);
        assertThat(result.event().getUnclassifiedDelta()).isEqualTo(0);
        verify(sexCompositionProjectionService).validateCandidate(any(), any(), any(), any());
    }

    @Test
    void rejectsSexAllocationThatDoesNotMatchPopulationDelta() {
        LocalDate date = LocalDate.now(ZoneId.of("Asia/Manila")).minusDays(1);
        when(dateValidation.resolve(date)).thenReturn(date);
        Batch batch = batch(55);
        when(batchService.requireBatchForUpdate(10L, 7L)).thenReturn(batch);
        when(sexCompositionProjectionService.hasBaseline(10L, 7L, date)).thenReturn(true);

        assertThatThrownBy(() -> service.record(
                10L, 7L, 21L,
                new CreateBatchEventRequest(date, EventType.CULLING, "Culling", null, 2,
                        null, null, UUID.randomUUID(), null,
                        new CreateBatchEventRequest.SexAllocation(-1, 0, 0))))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Male + female + unclassified changes must equal the population change");
        verify(eventRepository, never()).save(any(BatchEvent.class));
    }

    @Test
    void backdatedDeathUpdatesChronologicalSnapshotsButReturnsCurrentPopulation() {
        LocalDate date = LocalDate.now(ZoneId.of("Asia/Manila")).minusDays(2);
        LocalDate later = date.plusDays(1);
        Batch batch = batch(53);
        BatchEvent previous = new BatchEvent();
        previous.setId(5L);
        previous.setEventDate(later);
        previous.setEventType(EventType.HEALTH_DEATH);
        previous.setAffectedCount(2);
        previous.setPopulationDelta(-2);
        previous.setPopulationAfter(53);
        when(dateValidation.resolve(date)).thenReturn(date);
        when(dateValidation.today()).thenReturn(later.plusDays(1));
        when(batchService.requireBatchForUpdate(10L, 7L)).thenReturn(batch);
        when(eventRepository.findByBatchIdAndEventDateBetweenOrderByEventDateAscCreatedAtAsc(
                any(), any(), any())).thenReturn(List.of(previous));
        when(eventRepository.save(any(BatchEvent.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var result = service.record(10L, 7L, 21L,
                new CreateBatchEventRequest(date, EventType.ACCIDENTAL_DEATH,
                        "Accidental death", null, 1, null, null));

        assertThat(result.remainingPopulation()).isEqualTo(52);
        assertThat(result.event().getPopulationAfter()).isEqualTo(54);
        assertThat(previous.getPopulationAfter()).isEqualTo(52);
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
