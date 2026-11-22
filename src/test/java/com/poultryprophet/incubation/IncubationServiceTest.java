package com.poultryprophet.incubation;

import com.poultryprophet.batch.BatchService;
import com.poultryprophet.batch.dto.BatchResponse;
import com.poultryprophet.batch.dto.CreateBatchRequest;
import com.poultryprophet.common.BadRequestException;
import com.poultryprophet.incubation.dto.CompleteIncubationRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class IncubationServiceTest {
    @Mock private IncubationCycleRepository repository;
    @Mock private BatchService batchService;
    @Mock private BatchResponse batchResponse;

    @Test
    void forwardsBloodlineAndOptionalDerbyDateWhenCreatingFromAHatch() {
        Clock clock = Clock.fixed(Instant.parse("2026-09-12T00:00:00Z"), ZoneId.of("Asia/Manila"));
        IncubationCycle cycle = new IncubationCycle();
        cycle.setId(7L);
        cycle.setFarmId(3L);
        cycle.setCycleName("September");
        cycle.setStatus(IncubationStatus.COMPLETED);
        cycle.setActualHatchDate(LocalDate.of(2026, 9, 10));
        cycle.setHatchedCount(8);
        when(repository.findForUpdate(7L, 3L)).thenReturn(Optional.of(cycle));
        when(batchService.create(any(CreateBatchRequest.class), eq(3L))).thenReturn(batchResponse);
        when(batchResponse.id()).thenReturn(11L);

        IncubationService service = new IncubationService(repository, batchService, clock);
        service.createBatch(7L, 3L, LocalDate.of(2026, 9, 12), "Sweater", LocalDate.of(2027, 1, 15));

        ArgumentCaptor<CreateBatchRequest> request = ArgumentCaptor.forClass(CreateBatchRequest.class);
        verify(batchService).create(request.capture(), eq(3L));
        assertThat(request.getValue().bloodline()).isEqualTo("Sweater");
        assertThat(request.getValue().derbyDate()).isEqualTo(LocalDate.of(2027, 1, 15));
    }

    @Test
    void attentionIncludesOnlyDueOpenCyclesAndCompletedHatchesNeedingABatch() {
        Clock clock = Clock.fixed(Instant.parse("2026-11-19T04:00:00Z"), ZoneId.of("Asia/Manila"));
        IncubationCycle due = cycle(1L, IncubationStatus.LOADED, LocalDate.of(2026, 11, 19), null, null);
        IncubationCycle overdue = cycle(2L, IncubationStatus.HATCHING, LocalDate.of(2026, 11, 17), null, null);
        IncubationCycle future = cycle(3L, IncubationStatus.INCUBATING, LocalDate.of(2026, 11, 20), null, null);
        IncubationCycle needsBatch = cycle(4L, IncubationStatus.COMPLETED, LocalDate.of(2026, 11, 17), 4, null);
        IncubationCycle noChicks = cycle(5L, IncubationStatus.COMPLETED, LocalDate.of(2026, 11, 17), 0, null);
        IncubationCycle linked = cycle(6L, IncubationStatus.COMPLETED, LocalDate.of(2026, 11, 17), 4, 11L);
        IncubationCycle cancelled = cycle(7L, IncubationStatus.CANCELLED, LocalDate.of(2026, 11, 17), null, null);
        when(repository.findByFarmIdOrderByLoadedDateDescCreatedAtDesc(3L)).thenReturn(List.of(due, overdue, future, needsBatch, noChicks, linked, cancelled));

        var attention = new IncubationService(repository, batchService, clock).attention(3L);
        assertThat(attention).extracting("cycleId", "kind").containsExactlyInAnyOrder(
                org.assertj.core.groups.Tuple.tuple(1L, "HATCH_DUE_TODAY"),
                org.assertj.core.groups.Tuple.tuple(2L, "HATCH_OVERDUE"),
                org.assertj.core.groups.Tuple.tuple(4L, "BATCH_NEEDED"));
        assertThat(attention).allSatisfy(item -> assertThat(item.asOfDate()).isEqualTo(LocalDate.of(2026, 11, 19)));
        verify(repository).findByFarmIdOrderByLoadedDateDescCreatedAtDesc(3L);
    }

    @Test
    void zeroChicksCannotBecomeABatch() {
        Clock clock = Clock.fixed(Instant.parse("2026-11-19T04:00:00Z"), ZoneId.of("Asia/Manila"));
        IncubationCycle cycle = cycle(7L, IncubationStatus.COMPLETED, LocalDate.of(2026, 11, 17), 0, null);
        cycle.setActualHatchDate(LocalDate.of(2026, 11, 18));
        when(repository.findForUpdate(7L, 3L)).thenReturn(Optional.of(cycle));
        assertThatThrownBy(() -> new IncubationService(repository, batchService, clock)
                .createBatch(7L, 3L, LocalDate.of(2026, 11, 19), "Sweater", null))
                .isInstanceOf(BadRequestException.class).hasMessage("No chicks were recorded as hatched");
        verify(batchService, never()).create(any(), any());
    }

    @Test
    void alreadyLinkedBatchIsReturnedWithoutCreatingAnother() {
        Clock clock = Clock.fixed(Instant.parse("2026-11-19T04:00:00Z"), ZoneId.of("Asia/Manila"));
        IncubationCycle cycle = cycle(7L, IncubationStatus.COMPLETED, LocalDate.of(2026, 11, 17), 4, 11L);
        cycle.setActualHatchDate(LocalDate.of(2026, 11, 18));
        when(repository.findForUpdate(7L, 3L)).thenReturn(Optional.of(cycle));
        when(batchService.getForFarm(11L, 3L)).thenReturn(batchResponse);
        assertThat(new IncubationService(repository, batchService, clock)
                .createBatch(7L, 3L, LocalDate.of(2026, 11, 19), "Sweater", null)).isSameAs(batchResponse);
        verify(batchService, never()).create(any(), any());
    }

    private IncubationCycle cycle(long id, IncubationStatus status, LocalDate expected, Integer hatched, Long batchId) {
        IncubationCycle cycle = new IncubationCycle();
        cycle.setId(id);
        cycle.setFarmId(3L);
        cycle.setCycleName("Cycle " + id);
        cycle.setStatus(status);
        cycle.setExpectedHatchDate(expected);
        cycle.setHatchedCount(hatched);
        cycle.setCreatedBatchId(batchId);
        return cycle;
    }

    @Test
    void rejectsAnActualHatchDateInTheFuture() {
        Clock clock = Clock.fixed(Instant.parse("2026-09-12T00:00:00Z"), ZoneId.of("Asia/Manila"));
        IncubationCycle cycle = new IncubationCycle();
        cycle.setId(7L);
        cycle.setFarmId(3L);
        cycle.setLoadedDate(LocalDate.of(2026, 9, 1));
        cycle.setEggsLoaded(10);
        cycle.setStatus(IncubationStatus.INCUBATING);
        when(repository.findByIdAndFarmId(7L, 3L)).thenReturn(Optional.of(cycle));

        IncubationService service = new IncubationService(repository, batchService, clock);

        assertThatThrownBy(() -> service.complete(7L, 3L, 11L,
                new CompleteIncubationRequest(LocalDate.of(2026, 9, 13), 8, 1, 1, null)))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Actual hatch date cannot be in the future");
    }

    @Test
    void rejectsNegativeOrUnreconciledHatchCounts() {
        Clock clock = Clock.fixed(Instant.parse("2026-09-12T00:00:00Z"), ZoneId.of("Asia/Manila"));
        IncubationCycle cycle = new IncubationCycle();
        cycle.setId(8L);
        cycle.setFarmId(3L);
        cycle.setLoadedDate(LocalDate.of(2026, 9, 1));
        cycle.setEggsLoaded(10);
        cycle.setStatus(IncubationStatus.INCUBATING);
        when(repository.findByIdAndFarmId(8L, 3L)).thenReturn(Optional.of(cycle));

        IncubationService service = new IncubationService(repository, batchService, clock);

        assertThatThrownBy(() -> service.complete(8L, 3L, 11L,
                new CompleteIncubationRequest(LocalDate.of(2026, 9, 12), -1, 10, 1, null)))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Hatch result counts cannot be negative");
    }
}
