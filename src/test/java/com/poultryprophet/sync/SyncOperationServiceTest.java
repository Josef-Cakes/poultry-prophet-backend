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

@ExtendWith(MockitoExtension.class)
class SyncOperationServiceTest {
    @Mock private BatchEventService eventService;
    @Mock private FarmInputService inputService;
    @Mock private BatchEventRepository eventRepository;
    @Mock private FarmInputLogRepository inputRepository;
    @Mock private SelectionSessionService selectionSessionService;
    @Mock private BatchSelectionSessionRepository selectionSessionRepository;

    private ObjectMapper objectMapper;
    private SyncOperationService service;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().findAndRegisterModules();
        Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
        service = new SyncOperationService(objectMapper, eventService, inputService, eventRepository, inputRepository,
                selectionSessionService, selectionSessionRepository, validator);
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
