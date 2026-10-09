package com.poultryprophet.analytics;

import com.poultryprophet.batch.Batch;
import com.poultryprophet.batch.BatchRepository;
import com.poultryprophet.event.BatchEventRepository;
import com.poultryprophet.event.BatchEvent;
import com.poultryprophet.event.EventType;
import com.poultryprophet.input.FarmInputLogRepository;
import com.poultryprophet.selectionsession.SelectionSessionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BatchComparisonServiceTest {
    @Mock private BatchRepository batchRepository;
    @Mock private BatchEventRepository eventRepository;
    @Mock private FarmInputLogRepository inputRepository;
    @Mock private SelectionSessionService selectionSessionService;

    private static final java.time.ZoneId FARM_ZONE = java.time.ZoneId.of("Asia/Manila");
    private static final Clock FIXED_CLOCK = Clock.fixed(
            Instant.parse("2026-10-06T00:00:00Z"), FARM_ZONE);

    @Test
    void normalizesAllRowsToTheYoungestCommonWindowWithoutRanking() {
        LocalDate today = LocalDate.now(FIXED_CLOCK);
        Batch young = batch(1L, "Young batch", today.minusDays(9), 10);
        Batch older = batch(2L, "Older batch", today.minusDays(30), 20);
        when(batchRepository.findByIdAndFarmId(1L, 7L)).thenReturn(Optional.of(young));
        when(batchRepository.findByIdAndFarmId(2L, 7L)).thenReturn(Optional.of(older));
        when(eventRepository.findByBatchIdAndEventDateBetweenOrderByEventDateAscCreatedAtAsc(anyLong(), any(), any()))
                .thenReturn(List.of());
        when(inputRepository.findByFarmIdAndBatchIdOrderByRecordedAtDesc(7L, 1L)).thenReturn(List.of());
        when(inputRepository.findByFarmIdAndBatchIdOrderByRecordedAtDesc(7L, 2L)).thenReturn(List.of());
        when(selectionSessionService.latestFinalized(anyLong(), anyLong(), any())).thenReturn(null);

        BatchComparisonService service = new BatchComparisonService(batchRepository, eventRepository,
                inputRepository, selectionSessionService, "Asia/Manila",
                new com.poultryprophet.population.PopulationProjectionService(), FIXED_CLOCK);

        var result = service.compare(7L, List.of(1L, 2L), 30, "REAL");

        assertThat(result.requestedWindowDays()).isEqualTo(30);
        assertThat(result.effectiveWindowDays()).isEqualTo(10);
        assertThat(result.warnings()).anyMatch(value -> value.contains("normalized"));
        assertThat(result.batches()).hasSize(2);
        assertThat(result.batches()).allSatisfy(row -> assertThat(row.limitations()).contains("No event records for this common window."));
    }

    @Test
    void doesNotExposeANegativePopulationWhenAlegacyEventIsInconsistent() {
        LocalDate today = LocalDate.now(FIXED_CLOCK);
        Batch corrupt = batch(1L, "Boston sweater", today.minusDays(30), 100);
        Batch valid = batch(2L, "Bisaya", today.minusDays(30), 50);
        LocalDate expectedEnd = corrupt.getStartDate().plusDays(29);
        BatchEvent legacy = new BatchEvent();
        legacy.setId(91L);
        legacy.setEventDate(expectedEnd);
        legacy.setEventType(EventType.MORTALITY);
        legacy.setAffectedCount(161);
        legacy.setPopulationDelta(-161);
        when(batchRepository.findByIdAndFarmId(1L, 7L)).thenReturn(Optional.of(corrupt));
        when(batchRepository.findByIdAndFarmId(2L, 7L)).thenReturn(Optional.of(valid));
        when(eventRepository.findByBatchIdAndEventDateBetweenOrderByEventDateAscCreatedAtAsc(1L, corrupt.getStartDate(), expectedEnd))
                .thenReturn(List.of(legacy));
        when(eventRepository.findByBatchIdAndEventDateBetweenOrderByEventDateAscCreatedAtAsc(2L, valid.getStartDate(), expectedEnd))
                .thenReturn(List.of());
        when(inputRepository.findByFarmIdAndBatchIdOrderByRecordedAtDesc(7L, 1L)).thenReturn(List.of());
        when(inputRepository.findByFarmIdAndBatchIdOrderByRecordedAtDesc(7L, 2L)).thenReturn(List.of());
        when(selectionSessionService.latestFinalized(anyLong(), anyLong(), any())).thenReturn(null);

        BatchComparisonService service = new BatchComparisonService(batchRepository, eventRepository,
                inputRepository, selectionSessionService, "Asia/Manila",
                new com.poultryprophet.population.PopulationProjectionService(), FIXED_CLOCK);

        var result = service.compare(7L, List.of(1L, 2L), 30, "REAL");

        var corruptRow = result.batches().stream().filter(row -> row.batchId().equals(1L)).findFirst().orElseThrow();
        assertThat(corruptRow.populationAtWindowEnd()).isNull();
        assertThat(corruptRow.windowEnd()).isEqualTo(expectedEnd);
        assertThat(corruptRow.populationStatus()).isEqualTo("RECONCILIATION_REQUIRED");
        assertThat(corruptRow.populationWarning()).contains("outside the valid range");
        assertThat(result.warnings()).anyMatch(value -> value.contains("Boston sweater"));
    }

    private static Batch batch(Long id, String name, LocalDate start, int initial) {
        Batch batch = new Batch();
        batch.setId(id);
        batch.setFarmId(7L);
        batch.setName(name);
        batch.setStartDate(start);
        batch.setInitialPopulation(initial);
        batch.setCurrentPopulation(initial);
        return batch;
    }
}
