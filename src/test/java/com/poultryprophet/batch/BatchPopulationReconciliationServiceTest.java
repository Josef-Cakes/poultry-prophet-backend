package com.poultryprophet.batch;

import com.poultryprophet.common.ConflictException;
import com.poultryprophet.event.BatchEventRepository;
import com.poultryprophet.event.BatchEvent;
import com.poultryprophet.event.EventType;
import com.poultryprophet.population.PopulationProjectionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BatchPopulationReconciliationServiceTest {
    @Mock BatchService batches;
    @Mock BatchRepository batchRepository;
    @Mock BatchEventRepository events;
    @Mock BatchLifecycleAuditRepository audits;

    @Test
    void diagnosticsPermitOnlyAValidMismatchedLedgerAndStaleRevisionCannotRepair() {
        Batch batch = new Batch();
        batch.setId(7L); batch.setFarmId(3L); batch.setName("Flock");
        batch.setStartDate(LocalDate.of(2026, 1, 1));
        batch.setInitialPopulation(100); batch.setCurrentPopulation(90);
        when(batches.requireBatch(7L, 3L)).thenReturn(batch);
        when(batches.requireBatchForUpdate(7L, 3L)).thenReturn(batch);
        when(events.findByBatchIdAndEventDateBetweenOrderByEventDateAscCreatedAtAsc(eq(7L), any(), any())).thenReturn(List.of());
        var service = new BatchPopulationReconciliationService(batches, batchRepository, events, audits,
                new PopulationProjectionService(), "Asia/Manila");

        var diagnostics = service.diagnostics(7L, 3L);
        assertThat(diagnostics.status()).isEqualTo("STORED_MISMATCH");
        assertThat(diagnostics.canRepairStoredCount()).isTrue();
        assertThat(diagnostics.calculatedPopulation()).isEqualTo(100);

        batch.setCurrentPopulation(91);
        assertThatThrownBy(() -> service.reconcile(7L, 3L, 9L, diagnostics.revision()))
                .isInstanceOf(ConflictException.class).hasMessageContaining("STALE_DIAGNOSTICS");
        verify(batchRepository, never()).save(any());
        verify(audits, never()).save(any());
    }

    @Test
    void validMismatchRepairsOnlyTheStoredCountAndAuditsOnce() {
        Batch batch = new Batch();
        batch.setId(7L); batch.setFarmId(3L); batch.setName("Flock");
        batch.setStartDate(LocalDate.of(2026, 1, 1)); batch.setInitialPopulation(100); batch.setCurrentPopulation(90);
        when(batches.requireBatch(7L, 3L)).thenReturn(batch);
        when(batches.requireBatchForUpdate(7L, 3L)).thenReturn(batch);
        when(events.findByBatchIdAndEventDateBetweenOrderByEventDateAscCreatedAtAsc(eq(7L), any(), any())).thenReturn(List.of());
        var service = new BatchPopulationReconciliationService(batches, batchRepository, events, audits,
                new PopulationProjectionService(), "Asia/Manila");
        var diagnostics = service.diagnostics(7L, 3L);
        service.reconcile(7L, 3L, 9L, diagnostics.revision());
        assertThat(batch.getCurrentPopulation()).isEqualTo(100);
        verify(batchRepository, times(1)).save(batch);
        verify(audits, times(1)).save(any());
        service.reconcile(7L, 3L, 9L, service.diagnostics(7L, 3L).revision());
        verify(batchRepository, times(1)).save(batch);
        verify(audits, times(1)).save(any());
        verify(events, never()).save(any());
    }

    @Test
    void invalidLedgerCannotBeRepaired() {
        Batch batch = new Batch();
        batch.setId(7L); batch.setFarmId(3L); batch.setName("Flock");
        batch.setStartDate(LocalDate.of(2026, 1, 1)); batch.setInitialPopulation(10); batch.setCurrentPopulation(8);
        BatchEvent event = new BatchEvent();
        event.setId(12L); event.setEventType(EventType.HEALTH_DEATH); event.setEventDate(LocalDate.of(2026, 1, 2));
        event.setAffectedCount(11); event.setPopulationDelta(-11);
        when(batches.requireBatch(7L, 3L)).thenReturn(batch);
        when(batches.requireBatchForUpdate(7L, 3L)).thenReturn(batch);
        when(events.findByBatchIdAndEventDateBetweenOrderByEventDateAscCreatedAtAsc(eq(7L), any(), any())).thenReturn(List.of(event));
        var service = new BatchPopulationReconciliationService(batches, batchRepository, events, audits,
                new PopulationProjectionService(), "Asia/Manila");
        var diagnostics = service.diagnostics(7L, 3L);
        assertThat(diagnostics.status()).isEqualTo("INVALID_LEDGER");
        assertThat(diagnostics.firstInvalidEventId()).isEqualTo(12);
        assertThat(diagnostics.canRepairStoredCount()).isFalse();
        assertThatThrownBy(() -> service.reconcile(7L, 3L, 9L, diagnostics.revision())).isInstanceOf(ConflictException.class);
        verify(batchRepository, never()).save(any());
        verify(audits, never()).save(any());
    }
}
