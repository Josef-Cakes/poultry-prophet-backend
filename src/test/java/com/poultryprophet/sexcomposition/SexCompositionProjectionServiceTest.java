package com.poultryprophet.sexcomposition;

import com.poultryprophet.batch.Batch;
import com.poultryprophet.event.BatchEvent;
import com.poultryprophet.event.BatchEventRepository;
import com.poultryprophet.event.EventType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SexCompositionProjectionServiceTest {
    @Mock private BatchSexCompositionRepository compositionRepository;
    @Mock private BatchEventRepository eventRepository;

    @Test
    void projectsAttributedPopulationEventsFromTheManualBaseline() {
        Batch batch = batch();
        BatchSexComposition baseline = baseline(10L, 55, 40, 5, 0L);
        BatchEvent culling = event(11L, LocalDate.of(2026, 10, 9), -2, 0, 0);
        when(compositionRepository
                .findFirstByBatchIdAndFarmIdAndObservedOnLessThanEqualOrderByObservedOnDescCreatedAtDesc(
                        7L, 3L, LocalDate.of(2026, 10, 9)))
                .thenReturn(Optional.of(baseline));
        when(eventRepository.findByBatchIdAndEventDateBetweenOrderByEventDateAscCreatedAtAsc(
                7L, batch.getStartDate(), LocalDate.of(2026, 10, 9)))
                .thenReturn(List.of(culling));

        SexCompositionProjection result = new SexCompositionProjectionService(compositionRepository, eventRepository)
                .project(batch, 3L, LocalDate.of(2026, 10, 9));

        assertThat(result.status()).isEqualTo(SexCompositionProjection.VALID);
        assertThat(result.maleCount()).isEqualTo(53);
        assertThat(result.femaleCount()).isEqualTo(40);
        assertThat(result.unclassifiedCount()).isEqualTo(5);
        assertThat(result.total()).isEqualTo(98);
    }

    @Test
    void rejectsAnEventThatWouldMakeOneSexNegative() {
        Batch batch = batch();
        BatchSexComposition baseline = baseline(10L, 1, 40, 59, 0L);
        BatchEvent culling = event(null, LocalDate.of(2026, 10, 9), -2, 0, 0);
        when(compositionRepository
                .findFirstByBatchIdAndFarmIdAndObservedOnLessThanEqualOrderByObservedOnDescCreatedAtDesc(
                        7L, 3L, LocalDate.of(2026, 10, 9)))
                .thenReturn(Optional.of(baseline));
        when(eventRepository.findByBatchIdAndEventDateBetweenOrderByEventDateAscCreatedAtAsc(
                7L, batch.getStartDate(), LocalDate.of(2026, 10, 9)))
                .thenReturn(List.of());

        SexCompositionProjectionService service = new SexCompositionProjectionService(compositionRepository, eventRepository);

        assertThatThrownBy(() -> service.validateCandidate(batch, 3L, LocalDate.of(2026, 10, 9), culling))
                .isInstanceOf(com.poultryprophet.common.BadRequestException.class)
                .hasMessageContaining("negative");
    }

    private static Batch batch() {
        Batch batch = new Batch();
        batch.setId(7L);
        batch.setFarmId(3L);
        batch.setInitialPopulation(100);
        batch.setCurrentPopulation(98);
        batch.setStartDate(LocalDate.of(2026, 10, 1));
        return batch;
    }

    private static BatchSexComposition baseline(Long id, int male, int female, int unclassified, Long cursor) {
        BatchSexComposition value = new BatchSexComposition();
        value.setId(id);
        value.setFarmId(3L);
        value.setBatchId(7L);
        value.setObservedOn(LocalDate.of(2026, 10, 8));
        value.setPopulationAsOfObservation(male + female + unclassified);
        value.setMaleCount(male);
        value.setFemaleCount(female);
        value.setUnclassifiedCount(unclassified);
        value.setBaselineEventId(cursor);
        return value;
    }

    private static BatchEvent event(Long id, LocalDate date, int male, int female, int unclassified) {
        BatchEvent value = new BatchEvent();
        value.setId(id);
        value.setBatchId(7L);
        value.setEventDate(date);
        value.setEventType(EventType.CULLING);
        value.setCreatedAt(Instant.parse("2026-10-09T01:00:00Z"));
        value.setPopulationDelta(male + female + unclassified);
        value.setMaleDelta(male);
        value.setFemaleDelta(female);
        value.setUnclassifiedDelta(unclassified);
        value.setAffectedCount(Math.abs(male + female + unclassified));
        return value;
    }
}
