package com.poultryprophet.analytics;

import com.poultryprophet.batch.Batch;
import com.poultryprophet.batch.BatchRepository;
import com.poultryprophet.event.BatchEventRepository;
import com.poultryprophet.input.FarmInputLogRepository;
import com.poultryprophet.selectionsession.SelectionSessionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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

    @Test
    void normalizesAllRowsToTheYoungestCommonWindowWithoutRanking() {
        LocalDate today = LocalDate.now(java.time.ZoneId.of("Asia/Manila"));
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
                inputRepository, selectionSessionService, "Asia/Manila");

        var result = service.compare(7L, List.of(1L, 2L), 30, "REAL");

        assertThat(result.requestedWindowDays()).isEqualTo(30);
        assertThat(result.effectiveWindowDays()).isEqualTo(10);
        assertThat(result.warnings()).anyMatch(value -> value.contains("normalized"));
        assertThat(result.batches()).hasSize(2);
        assertThat(result.batches()).allSatisfy(row -> assertThat(row.limitations()).contains("No event records for this common window."));
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
