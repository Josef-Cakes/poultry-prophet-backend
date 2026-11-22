package com.poultryprophet.batch;

import com.poultryprophet.batch.dto.BatchResponse;
import com.poultryprophet.common.ConflictException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BatchRetirementServiceTest {
    @Mock private BatchRepository batchRepository;
    @Mock private BatchHandlerAssignmentRepository assignmentRepository;
    @Mock private BatchLifecycleAuditRepository auditRepository;
    @Mock private BatchService batchService;
    @Mock private EntityManager entityManager;
    @Mock private Query query;

    @Test
    void archivesUsingOnlyTheOpenTaskPreflight() {
        Batch batch = batch();
        when(batchService.requireBatchForUpdate(7L, 3L)).thenReturn(batch);
        when(entityManager.createNativeQuery(anyString())).thenReturn(query);
        when(query.setParameter("batchId", 7L)).thenReturn(query);
        when(query.getSingleResult()).thenReturn(0L);
        when(batchRepository.updateArchiveLifecycle(eq(7L), eq(3L), eq(BatchStatus.ACTIVE),
                eq(BatchStatus.ARCHIVED), any(), eq(11L), isNull(String.class))).thenAnswer(invocation -> {
            batch.setPreArchiveStatus(BatchStatus.ACTIVE);
            batch.setStatus(BatchStatus.ARCHIVED);
            return 1;
        });
        when(batchService.requireBatch(7L, 3L)).thenReturn(batch);
        when(batchService.responseFor(batch)).thenReturn(null);

        new BatchRetirementService(batchRepository, assignmentRepository, auditRepository,
                batchService, entityManager).archive(7L, 3L, 11L, null);

        assertThat(batch.getStatus()).isEqualTo(BatchStatus.ARCHIVED);
        verify(entityManager).createNativeQuery(contains("from handler_task"));
        verify(entityManager, times(1)).createNativeQuery(anyString());
        verify(auditRepository).save(any(BatchLifecycleAudit.class));
    }

    @Test
    void blocksArchiveWhenTheBatchHasOpenTasks() {
        Batch batch = batch();
        when(batchService.requireBatchForUpdate(7L, 3L)).thenReturn(batch);
        when(entityManager.createNativeQuery(anyString())).thenReturn(query);
        when(query.setParameter("batchId", 7L)).thenReturn(query);
        when(query.getSingleResult()).thenReturn(2L);

        assertThatThrownBy(() -> new BatchRetirementService(batchRepository, assignmentRepository,
                auditRepository, batchService, entityManager).archive(7L, 3L, 11L, null))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("2 open batch task(s)");

        verify(batchRepository, never()).updateArchiveLifecycle(anyLong(), anyLong(), any(), any(), any(), any(), any());
        verifyNoInteractions(auditRepository);
    }

    @Test
    void deletesArchivedBatchWithHistoryInDependencyOrderAndKeepsAudit() {
        Batch batch = batch(); batch.setStatus(BatchStatus.ARCHIVED);
        when(batchService.requireBatchForUpdate(7L, 3L)).thenReturn(batch);
        when(entityManager.createNativeQuery(anyString())).thenReturn(query);
        when(query.setParameter("batchId", 7L)).thenReturn(query);
        when(query.getSingleResult()).thenReturn(2L);
        when(assignmentRepository.findByBatchId(7L)).thenReturn(java.util.List.of());
        new BatchRetirementService(batchRepository, assignmentRepository, auditRepository,
                batchService, entityManager).delete(7L, 3L, 11L,
                new com.poultryprophet.batch.dto.DeleteBatchRequest("Validation batch", "Test cleanup"));
        var order = inOrder(entityManager, batchRepository);
        order.verify(entityManager).createNativeQuery(startsWith("delete from handler_task_status_history"));
        order.verify(entityManager).createNativeQuery(startsWith("delete from batch_selection_session_criterion"));
        order.verify(entityManager).createNativeQuery(startsWith("delete from alert"));
        order.verify(entityManager).createNativeQuery(startsWith("delete from indicator"));
        order.verify(entityManager).createNativeQuery(startsWith("delete from daily_record"));
        order.verify(entityManager).createNativeQuery(startsWith("delete from batch_selection_session where"));
        order.verify(entityManager).createNativeQuery(startsWith("delete from batch_selection_review"));
        order.verify(batchRepository).delete(batch);
        verify(auditRepository).save(argThat(row -> row.getAction().equals("DELETE") && row.getMetadataJson().contains("deletedArchivedBatch")));
        verify(entityManager).createNativeQuery(startsWith("update incubation_cycle set created_batch_id = null"));
    }

    @Test
    void rejectsPermanentDeletionOfActiveBatch() {
        when(batchService.requireBatchForUpdate(7L, 3L)).thenReturn(batch());
        assertThatThrownBy(() -> new BatchRetirementService(batchRepository, assignmentRepository,
                auditRepository, batchService, entityManager).delete(7L, 3L, 11L,
                new com.poultryprophet.batch.dto.DeleteBatchRequest("Validation batch", null)))
                .isInstanceOf(ConflictException.class).hasMessageContaining("BATCH_MUST_BE_ARCHIVED");
        verifyNoInteractions(entityManager, auditRepository);
        verify(batchRepository, never()).delete(any());
    }

    @Test
    void rejectsDeletionWhenNameDoesNotMatch() {
        Batch batch = batch(); batch.setStatus(BatchStatus.ARCHIVED);
        when(batchService.requireBatchForUpdate(7L, 3L)).thenReturn(batch);
        assertThatThrownBy(() -> new BatchRetirementService(batchRepository, assignmentRepository,
                auditRepository, batchService, entityManager).delete(7L, 3L, 11L,
                new com.poultryprophet.batch.dto.DeleteBatchRequest("Wrong", null)))
                .isInstanceOf(com.poultryprophet.common.BadRequestException.class);
        verifyNoInteractions(entityManager, auditRepository);
        verify(batchRepository, never()).delete(any());
    }

    private static Batch batch() {
        Batch batch = new Batch();
        batch.setId(7L);
        batch.setFarmId(3L);
        batch.setName("Validation batch");
        batch.setInitialPopulation(50);
        batch.setCurrentPopulation(50);
        batch.setStartDate(LocalDate.of(2026, 9, 1));
        batch.setStatus(BatchStatus.ACTIVE);
        return batch;
    }
}
