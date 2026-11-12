package com.poultryprophet.sync;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.poultryprophet.event.BatchEventRepository;
import com.poultryprophet.event.BatchEventService;
import com.poultryprophet.input.FarmInputLogRepository;
import com.poultryprophet.input.FarmInputService;
import com.poultryprophet.selectionsession.BatchSelectionSessionRepository;
import com.poultryprophet.selectionsession.CreateSelectionSessionRequest;
import com.poultryprophet.selectionsession.SelectionSessionResponse;
import com.poultryprophet.selectionsession.SelectionSessionService;
import com.poultryprophet.selectionsession.SelectionSessionStatus;
import com.poultryprophet.sync.dto.SyncOperationRequest;
import com.poultryprophet.sync.dto.SyncOperationsRequest;
import com.poultryprophet.sync.dto.SyncOperationsResponse;
import com.poultryprophet.user.Role;
import com.poultryprophet.record.DailyRecordService;
import org.springframework.jdbc.core.JdbcTemplate;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
class SyncOperationServiceTest {
    @Mock private BatchEventService eventService;
    @Mock private FarmInputService inputService;
    @Mock private BatchEventRepository eventRepository;
    @Mock private FarmInputLogRepository inputRepository;
    @Mock private SelectionSessionService selectionSessionService;
    @Mock private BatchSelectionSessionRepository selectionSessionRepository;
    @Mock private DailyRecordService dailyRecordService;
    @Mock private JdbcTemplate jdbcTemplate;

    private ObjectMapper objectMapper;
    private SyncOperationService service;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().findAndRegisterModules();
        Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
        service = new SyncOperationService(objectMapper, eventService, inputService, eventRepository, inputRepository,
                selectionSessionService, selectionSessionRepository, validator, null, null, null, null,
                java.time.Clock.system(java.time.ZoneId.of("Asia/Manila")), null, dailyRecordService, jdbcTemplate);
    }

    @Test
    void appliesManagerSelectionSessionDraftThroughTheOutbox() {
        UUID operationId = UUID.randomUUID();
        CreateSelectionSessionRequest payload = request(operationId);
        when(selectionSessionRepository.findByOperationId(operationId)).thenReturn(java.util.Optional.empty());
        when(selectionSessionService.create(7L, 2L, 4L, payload, null)).thenReturn(response(81L, operationId));

        SyncOperationsResponse result = service.sync(syncRequest(operationId, "SELECTION_SESSION", objectMapper.valueToTree(payload)),
                2L, 4L, Role.MANAGER);

        assertEquals("APPLIED", result.results().getFirst().status());
        assertEquals(81L, result.results().getFirst().serverId());
        verify(selectionSessionService).create(7L, 2L, 4L, payload, null);
    }

    @Test
    void rejectsSelectionSessionOutboxOperationsFromHandlers() {
        UUID operationId = UUID.randomUUID();
        CreateSelectionSessionRequest payload = request(operationId);

        SyncOperationsResponse result = service.sync(syncRequest(operationId, "SELECTION_SESSION", objectMapper.valueToTree(payload)),
                2L, 4L, Role.HANDLER);

        assertEquals("REJECTED", result.results().getFirst().status());
        verifyNoInteractions(selectionSessionService, selectionSessionRepository);
    }

    @Test
    void appliesAnOfflineDraftUpdateToTheTargetSession() {
        UUID operationId = UUID.randomUUID();
        CreateSelectionSessionRequest payload = request(operationId);
        ObjectNode update = objectMapper.valueToTree(payload);
        update.put("sessionId", 81L);
        when(selectionSessionService.updateDraft(7L, 2L, 81L, payload)).thenReturn(response(81L, operationId));

        SyncOperationsResponse result = service.sync(syncRequest(operationId, "SELECTION_SESSION_UPDATE", update),
                2L, 4L, Role.MANAGER);

        assertEquals("APPLIED", result.results().getFirst().status());
        assertEquals(81L, result.results().getFirst().serverId());
        verify(selectionSessionService).updateDraft(7L, 2L, 81L, payload);
    }

    @Test
    void rejectsTaskReplayWithoutAStatus() {
        UUID operationId = UUID.randomUUID();
        ObjectNode payload = objectMapper.createObjectNode().put("taskId", 12);
        SyncOperationsResponse result = service.sync(syncRequest(operationId, "TASK_STATUS", payload), 2L, 4L, Role.HANDLER);
        assertEquals("REJECTED", result.results().getFirst().status());
    }

    @Test
    void rejectedMeasurementBlocksLaterOperationsForTheSameBatch() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        ObjectNode invalid = objectMapper.createObjectNode().put("recordDate", "2026-10-07").put("temperatureC", 99)
                .put("operationId", first.toString()).put("ownerUserId", 4).put("ownerFarmId", 2);
        var operations = new SyncOperationsRequest("test-device", java.util.List.of(
                new SyncOperationRequest(first, 1, "DAILY_RECORD", 7L, Instant.parse("2026-10-07T04:00:00Z"), invalid),
                new SyncOperationRequest(second, 1, "TASK_STATUS", 7L, Instant.parse("2026-10-07T04:01:00Z"), objectMapper.createObjectNode())));
        var results = service.sync(operations, 2L, 4L, Role.HANDLER).results();
        assertEquals("REJECTED", results.get(0).status());
        assertEquals("RETRYABLE", results.get(1).status());
    }

    @Test
    void rejectsInvalidMeasurementBeforeWritingAnything() {
        UUID id = UUID.randomUUID();
        ObjectNode payload = objectMapper.createObjectNode().put("recordDate", "2026-10-07")
                .put("temperatureC", 99).put("operationId", id.toString()).put("ownerUserId", 4).put("ownerFarmId", 2);
        assertEquals("REJECTED", service.sync(syncRequest(id, "DAILY_RECORD", payload), 2L, 4L, Role.HANDLER)
                .results().getFirst().status());
        verifyNoInteractions(dailyRecordService, jdbcTemplate);
    }

    @Test
    void retriesReturnOriginalReceiptWithoutWritingTheRecordAgain() {
        UUID id = UUID.randomUUID();
        ObjectNode payload = objectMapper.createObjectNode().put("recordDate", "2026-10-07")
                .put("temperatureC", 30).put("operationId", id.toString()).put("ownerUserId", 4).put("ownerFarmId", 2);
        when(jdbcTemplate.update(any(String.class), any(Object[].class))).thenReturn(0);
        when(jdbcTemplate.query(any(String.class), org.mockito.ArgumentMatchers.<org.springframework.jdbc.core.RowMapper<Long>>any(),
                any(Object[].class))).thenReturn(java.util.List.of(42L));
        var result = service.sync(syncRequest(id, "DAILY_RECORD", payload), 2L, 4L, Role.HANDLER).results().getFirst();
        assertEquals("ALREADY_APPLIED", result.status());
        assertEquals(42L, result.serverId());
        verifyNoInteractions(dailyRecordService);
    }

    @Test
    void firstMeasurementWritesAReceiptAndTheRecord() {
        UUID id = UUID.randomUUID();
        ObjectNode payload = objectMapper.createObjectNode().put("recordDate", "2026-10-07")
                .put("temperatureC", 30).put("operationId", id.toString()).put("ownerUserId", 4).put("ownerFarmId", 2);
        when(jdbcTemplate.update(any(String.class), any(Object[].class))).thenReturn(1);
        var saved = mock(com.poultryprophet.record.dto.DailyRecordResponse.class);
        when(saved.id()).thenReturn(42L);
        when(dailyRecordService.recordOffline(eq(7L), eq(2L), any(), eq(4L))).thenReturn(saved);
        var result = service.sync(syncRequest(id, "DAILY_RECORD", payload), 2L, 4L, Role.HANDLER).results().getFirst();
        assertEquals("APPLIED", result.status());
        assertEquals(42L, result.serverId());
        verify(jdbcTemplate).update(org.mockito.ArgumentMatchers.startsWith("UPDATE offline_daily_record_receipt"), eq(42L), eq(id));
    }

    @Test
    void reusedOperationWithDifferentPayloadOrScopeIsAConflict() {
        UUID id = UUID.randomUUID();
        ObjectNode payload = objectMapper.createObjectNode().put("recordDate", "2026-10-07")
                .put("temperatureC", 30).put("operationId", id.toString()).put("ownerUserId", 4).put("ownerFarmId", 2);
        when(jdbcTemplate.update(any(String.class), any(Object[].class))).thenReturn(0);
        when(jdbcTemplate.query(any(String.class), org.mockito.ArgumentMatchers.<org.springframework.jdbc.core.RowMapper<Long>>any(),
                any(Object[].class))).thenReturn(java.util.List.of());
        assertEquals("CONFLICT", service.sync(syncRequest(id, "DAILY_RECORD", payload), 2L, 4L, Role.HANDLER)
                .results().getFirst().status());
        verifyNoInteractions(dailyRecordService);
    }

    @Test
    void rejectsMeasurementsFromAnotherUserOrFarmBeforeWriting() {
        UUID id = UUID.randomUUID();
        ObjectNode payload = objectMapper.createObjectNode().put("recordDate", "2026-10-07")
                .put("temperatureC", 30).put("operationId", id.toString()).put("ownerUserId", 4).put("ownerFarmId", 2);
        assertEquals("CONFLICT", service.sync(syncRequest(id, "DAILY_RECORD", payload), 2L, 5L, Role.HANDLER).results().getFirst().status());
        assertEquals("CONFLICT", service.sync(syncRequest(id, "DAILY_RECORD", payload), 3L, 4L, Role.HANDLER).results().getFirst().status());
        verifyNoInteractions(dailyRecordService, jdbcTemplate);
    }

    @Test
    void rollsBackReceiptReservationWhenTheRecordConflicts() {
        var transactionManager = mock(org.springframework.transaction.PlatformTransactionManager.class);
        var transaction = new org.springframework.transaction.support.SimpleTransactionStatus();
        when(transactionManager.getTransaction(any())).thenReturn(transaction);
        service = new SyncOperationService(objectMapper, eventService, inputService, eventRepository, inputRepository,
                selectionSessionService, selectionSessionRepository, Validation.buildDefaultValidatorFactory().getValidator(),
                null, null, null, null, java.time.Clock.systemUTC(), transactionManager, dailyRecordService, jdbcTemplate);
        UUID id = UUID.randomUUID();
        ObjectNode payload = objectMapper.createObjectNode().put("recordDate", "2026-10-07")
                .put("temperatureC", 30).put("operationId", id.toString()).put("ownerUserId", 4).put("ownerFarmId", 2);
        when(jdbcTemplate.update(any(String.class), any(Object[].class))).thenReturn(1);
        when(dailyRecordService.recordOffline(eq(7L), eq(2L), any(), eq(4L)))
                .thenThrow(new com.poultryprophet.common.ConflictException("already exists"));
        assertEquals("CONFLICT", service.sync(syncRequest(id, "DAILY_RECORD", payload), 2L, 4L, Role.HANDLER).results().getFirst().status());
        verify(transactionManager).rollback(transaction);
    }

    @Test
    void replayDerivesTheFarmDateRatherThanTheUtcDate() {
        UUID operationId = UUID.randomUUID();
        ObjectNode payload = objectMapper.createObjectNode().put("eventType", "HEALTH_CONCERN")
                .put("title", "Observation").put("affectedCount", 0).put("operationId", operationId.toString());
        var saved = mock(com.poultryprophet.event.dto.BatchEventResponse.class);
        when(saved.id()).thenReturn(42L);
        when(eventService.create(eq(7L), eq(2L), eq(4L), any())).thenReturn(saved);
        var request = new SyncOperationsRequest("test-device", java.util.List.of(new SyncOperationRequest(
                operationId, 1, "BATCH_EVENT", 7L, Instant.parse("2026-10-06T16:30:00Z"), payload)));
        assertEquals("APPLIED", service.sync(request, 2L, 4L, Role.HANDLER).results().getFirst().status());
        var captured = org.mockito.ArgumentCaptor.forClass(com.poultryprophet.event.dto.CreateBatchEventRequest.class);
        verify(eventService).create(eq(7L), eq(2L), eq(4L), captured.capture());
        assertEquals(LocalDate.of(2026, 10, 7), captured.getValue().eventDate());
    }

    private CreateSelectionSessionRequest request(UUID operationId) {
        return new CreateSelectionSessionRequest(LocalDate.of(2026, 10, 7), 4, 1, 1, 1, 0,
                Set.of("GENERAL_PHYSICAL_CONDITION"), null, null, operationId);
    }

    private SyncOperationsRequest syncRequest(UUID operationId, String entityType,
                                              com.fasterxml.jackson.databind.JsonNode payload) {
        return new SyncOperationsRequest("test-device", java.util.List.of(new SyncOperationRequest(
                operationId, 1, entityType, 7L, Instant.parse("2026-10-07T04:00:00Z"), payload)));
    }

    private SelectionSessionResponse response(Long sessionId, UUID operationId) {
        Instant now = Instant.parse("2026-10-07T04:00:00Z");
        return new SelectionSessionResponse(sessionId, 2L, 7L, LocalDate.of(2026, 10, 7), 4L,
                4, 1, 1, 1, 0, 25.0, 1, 4, SelectionSessionStatus.DRAFT,
                Set.of("GENERAL_PHYSICAL_CONDITION"), null, null, operationId.toString(),
                null, now, now, null);
    }
}
