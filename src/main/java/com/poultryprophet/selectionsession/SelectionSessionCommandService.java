package com.poultryprophet.selectionsession;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.poultryprophet.common.BadRequestException;
import com.poultryprophet.common.ConflictException;
import com.poultryprophet.sync.CommandReceiptService;
import org.springframework.stereotype.Service;
import java.util.Locale;

/** One entry boundary for direct-online and outbox draft updates. */
@Service
public class SelectionSessionCommandService {
    private final SelectionSessionService sessions;
    private final CommandReceiptService receipts;
    private final ObjectMapper mapper;
    public SelectionSessionCommandService(SelectionSessionService sessions, CommandReceiptService receipts, ObjectMapper mapper) {
        this.sessions = sessions; this.receipts = receipts; this.mapper = mapper;
    }
    public CommandReceiptService.Execution<SelectionSessionResponse> update(Long batchId, Long farmId, Long userId,
                                                                          Long sessionId, CreateSelectionSessionRequest request) {
        if (request == null || request.operationId() == null) throw new BadRequestException("A selection edit command ID is required");
        ObjectNode payload = mapper.valueToTree(request);
        payload.put("batchId", batchId);
        var codes = mapper.createArrayNode();
        if (request.criterionCodes() != null) request.criterionCodes().stream().filter(java.util.Objects::nonNull)
                .map(String::trim).filter(value -> !value.isEmpty()).map(value -> value.toUpperCase(Locale.ROOT))
                .distinct().sorted().forEach(codes::add);
        payload.set("criterionCodes", codes);
        return receipts.executeOnce(new CommandReceiptService.CommandIdentity(request.operationId(), 1, farmId, userId,
                "SELECTION_SESSION_UPDATE", "SELECTION_SESSION", sessionId), payload, SelectionSessionResponse.class, () -> {
            // Receipts are checked BEFORE mutable lifecycle/revision checks. An
            // old unreceipted edit cannot be proven from the draft's current state.
            if (request.expectedVersion() == null) throw new ConflictException(
                    "This legacy selection edit has no revision evidence. Keep it for review; it cannot safely overwrite the current draft.");
            return sessions.updateDraft(batchId, farmId, sessionId, request);
        });
    }
}
