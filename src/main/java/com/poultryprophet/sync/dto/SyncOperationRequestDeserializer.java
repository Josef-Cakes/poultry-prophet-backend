package com.poultryprophet.sync.dto;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

/** Normalize only the known legacy sex-count envelope, preserving Jackson's
 * configured decoding for every other field/timestamp representation. */
public final class SyncOperationRequestDeserializer extends StdDeserializer<SyncOperationRequest> {
    public SyncOperationRequestDeserializer() { super(SyncOperationRequest.class); }

    @Override
    public SyncOperationRequest deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        JsonNode tree = parser.readValueAsTree();
        JsonNode occurred = tree.path("occurredAt");
        if (tree instanceof ObjectNode object && "SEX_COMPOSITION".equals(tree.path("entityType").asText())
                && occurred.isTextual() && occurred.asText().matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")) {
            try {
                object.put("occurredAt", LocalDate.parse(occurred.asText())
                        .atStartOfDay(ZoneId.of("Asia/Manila")).toInstant().toString());
            } catch (java.time.DateTimeException error) {
                return (SyncOperationRequest) context.handleWeirdStringValue(SyncOperationRequest.class,
                        occurred.asText(), "Invalid legacy sex-count date");
            }
        }
        Wire wire = context.readTreeAsValue(tree, Wire.class);
        return new SyncOperationRequest(wire.operationId(), wire.schemaVersion(), wire.entityType(),
                wire.batchId(), wire.occurredAt(), wire.payload());
    }

    private record Wire(UUID operationId, int schemaVersion, String entityType, Long batchId,
                        Instant occurredAt, JsonNode payload) {}
}
