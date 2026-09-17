package com.poultryprophet.alert;

import com.poultryprophet.analytics.Indicator;
import com.poultryprophet.analytics.ThresholdConfigRepository;
import com.poultryprophet.batch.Batch;
import com.poultryprophet.batch.BatchRepository;
import com.poultryprophet.batch.BatchService;
import com.poultryprophet.event.BatchEvent;
import com.poultryprophet.event.BatchEventRepository;
import com.poultryprophet.event.MortalityRecordedEvent;
import com.poultryprophet.realtime.RealtimeNotificationService;
import com.poultryprophet.user.User;
import com.poultryprophet.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AlertServiceTest {

    @Mock private AlertRepository alertRepository;
    @Mock private SeverityClassifier severityClassifier;
    @Mock private ThresholdConfigRepository thresholdRepository;
    @Mock private RealtimeNotificationService realtime;
    @Mock private UserRepository userRepository;
    @Mock private BatchService batchService;
    @Mock private BatchRepository batchRepository;
    @Mock private BatchEventRepository batchEventRepository;

    @InjectMocks
    private AlertService service;

    @Test
    void createsWarningMortalityAlertLinkedToItsSourceEvent() {
        Batch batch = batch(52);
        User handler = handler("Pedro Santos");
        BatchEvent source = new BatchEvent();
        source.setId(99L);
        when(alertRepository.existsBySourceEvent_Id(99L)).thenReturn(false);
        when(batchRepository.getReferenceById(10L)).thenReturn(batch);
        when(userRepository.findById(21L)).thenReturn(Optional.of(handler));
        when(batchEventRepository.getReferenceById(99L)).thenReturn(source);
        when(alertRepository.save(any(Alert.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.onMortalityRecorded(new MortalityRecordedEvent(
                99L, 10L, 21L, LocalDate.of(2026, 9, 12), 3, 52, "Heat stress"));

        ArgumentCaptor<Alert> captor = ArgumentCaptor.forClass(Alert.class);
        verify(alertRepository).save(captor.capture());
        Alert alert = captor.getValue();
        assertThat(alert.getSourceEvent()).isSameAs(source);
        assertThat(alert.getIndicatorType()).isEqualTo("HEALTH_DEATH");
        assertThat(alert.getSeverity()).isEqualTo(Severity.WARNING);
        assertThat(alert.getBatchName()).isEqualTo("September flock");
        assertThat(alert.getHandlerName()).isEqualTo("Pedro Santos");
        assertThat(alert.getDeathCount()).isEqualTo(3);
        assertThat(alert.getCause()).isEqualTo("Heat stress");
        assertThat(alert.getOccurrenceDate()).isEqualTo(LocalDate.of(2026, 9, 12));
        verify(realtime).publishAlertCreated(alert);
    }

    @Test
    void usesCriticalSeverityWhenMortalityEmptiesTheBatch() {
        Batch batch = batch(0);
        when(alertRepository.existsBySourceEvent_Id(99L)).thenReturn(false);
        when(batchRepository.getReferenceById(10L)).thenReturn(batch);
        when(userRepository.findById(21L)).thenReturn(Optional.empty());
        when(batchEventRepository.getReferenceById(99L)).thenReturn(new BatchEvent());
        when(alertRepository.save(any(Alert.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.onMortalityRecorded(new MortalityRecordedEvent(
                99L, 10L, 21L, LocalDate.of(2026, 9, 13), 1, 0, "Unknown"));

        ArgumentCaptor<Alert> captor = ArgumentCaptor.forClass(Alert.class);
        verify(alertRepository).save(captor.capture());
        assertThat(captor.getValue().getSeverity()).isEqualTo(Severity.CRITICAL);
    }

    private static Batch batch(int currentPopulation) {
        Batch batch = new Batch();
        batch.setId(10L);
        batch.setFarmId(7L);
        batch.setName("September flock");
        batch.setCurrentPopulation(currentPopulation);
        return batch;
    }

    private static User handler(String name) {
        User handler = new User();
        handler.setId(21L);
        handler.setFullName(name);
        return handler;
    }
}
