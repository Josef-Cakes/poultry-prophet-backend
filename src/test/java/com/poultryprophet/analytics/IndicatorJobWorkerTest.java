package com.poultryprophet.analytics;

import com.poultryprophet.batch.Batch;
import com.poultryprophet.batch.BatchRepository;
import com.poultryprophet.config.AnalyticsProperties;
import com.poultryprophet.realtime.RealtimeNotificationService;
import com.poultryprophet.record.DailyRecord;
import com.poultryprophet.record.DailyRecordRepository;
import com.poultryprophet.record.event.RecordCreatedEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IndicatorJobWorkerTest {

    @Mock private DailyRecordRepository recordRepository;
    @Mock private BatchRepository batchRepository;
    @Mock private IndicatorRepository indicatorRepository;
    @Mock private AnalyticsService analyticsService;
    @Mock private RealtimeNotificationService realtime;

    @Test
    void recomputesEveryObservationInChronologicalOrderAfterBackdatedWrite() {
        AnalyticsProperties properties = new AnalyticsProperties();
        IndicatorJobWorker worker = new IndicatorJobWorker(recordRepository, batchRepository,
                indicatorRepository, analyticsService, realtime, properties);
        Batch batch = new Batch();
        batch.setId(10L);
        DailyRecord older = record(1L, "2026-09-12");
        DailyRecord middle = record(2L, "2026-09-13");
        DailyRecord latest = record(3L, "2026-09-14");
        when(batchRepository.findById(10L)).thenReturn(Optional.of(batch));
        when(recordRepository.findByBatchIdOrderByRecordDateAsc(10L))
                .thenReturn(List.of(older, middle, latest));
        when(indicatorRepository.findByRecordId(any())).thenReturn(Optional.empty());
        when(indicatorRepository.save(any(Indicator.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(analyticsService.compute(any(), any())).thenReturn(result());

        worker.onRecordCreated(new RecordCreatedEvent(middle.getId(), 10L));

        ArgumentCaptor<List<DailyRecord>> windows = ArgumentCaptor.forClass(List.class);
        verify(analyticsService, org.mockito.Mockito.times(3)).compute(windows.capture(), any());
        assertThat(windows.getAllValues()).hasSize(3);
        assertThat(windows.getAllValues().get(0)).extracting(DailyRecord::getRecordDate)
                .containsExactly(LocalDate.of(2026, 9, 12));
        assertThat(windows.getAllValues().get(2)).extracting(DailyRecord::getRecordDate)
                .containsExactly(LocalDate.of(2026, 9, 14), LocalDate.of(2026, 9, 13), LocalDate.of(2026, 9, 12));
        verify(realtime).publishIndicatorUpdated(any(Indicator.class));
    }

    private static DailyRecord record(Long id, String date) {
        DailyRecord record = new DailyRecord();
        record.setId(id);
        record.setRecordDate(LocalDate.parse(date));
        return record;
    }

    private static IndicatorResult result() {
        return new IndicatorResult(80.0, 10.0, 1.8, null,
                90.0, 95.0, 80.0, 70.0,
                27.0, 33.25, 16.0, 10.5,
                true, null, AnalyticsService.FORMULA_VERSION);
    }
}
