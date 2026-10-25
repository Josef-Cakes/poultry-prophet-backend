package com.poultryprophet.incubation;

import com.poultryprophet.batch.BatchService;
import com.poultryprophet.common.BadRequestException;
import com.poultryprophet.incubation.dto.CompleteIncubationRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IncubationServiceTest {
    @Mock private IncubationCycleRepository repository;
    @Mock private BatchService batchService;

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
