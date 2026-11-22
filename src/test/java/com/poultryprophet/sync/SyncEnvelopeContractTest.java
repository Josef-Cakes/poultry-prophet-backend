package com.poultryprophet.sync;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.poultryprophet.sync.dto.SyncOperationRequest;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import static org.junit.jupiter.api.Assertions.*;
class SyncEnvelopeContractTest {
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private String json(String type, String occurred) {
        return """
        {"operationId":"00000000-0000-4000-8000-000000000001","schemaVersion":1,
         "entityType":"%s","batchId":7,"occurredAt":"%s","payload":{"observedOn":"2026-11-22"}}
        """.formatted(type, occurred);
    }
    @Test void legacyQueuedSexCountDateRemainsReplayableWithoutChangingIdentity() throws Exception {
        var command = mapper.readValue(json("SEX_COMPOSITION", "2026-11-22"), SyncOperationRequest.class);
        assertEquals(Instant.parse("2026-11-21T16:00:00Z"), command.occurredAt());
        assertEquals("00000000-0000-4000-8000-000000000001", command.operationId().toString());
        assertEquals("2026-11-22", command.payload().path("observedOn").asText());
    }
    @Test void timestampInstantsKeepTheirOriginalValue() throws Exception {
        var command = mapper.readValue(json("SEX_COMPOSITION", "2026-11-22T03:04:05.123Z"), SyncOperationRequest.class);
        assertEquals(Instant.parse("2026-11-22T03:04:05.123Z"), command.occurredAt());
    }
    @Test void invalidDatesAndOtherDateOnlyCommandsAreNotSilentlyReinterpreted() {
        assertThrows(JsonProcessingException.class, () -> mapper.readValue(json("SEX_COMPOSITION", "2026-02-30"), SyncOperationRequest.class));
        assertThrows(JsonProcessingException.class, () -> mapper.readValue(json("DAILY_RECORD", "2026-11-22"), SyncOperationRequest.class));
    }
    @Test void nonLegacyWireTimestampsRetainJacksonCompatibility() throws Exception {
        var epoch = json("BATCH_EVENT", "unused").replace("\"unused\"", "0");
        assertEquals(Instant.EPOCH, mapper.readValue(epoch, SyncOperationRequest.class).occurredAt());
        var padded = mapper.readValue(json("BATCH_EVENT", " 2026-11-22T03:04:05Z "), SyncOperationRequest.class);
        assertEquals(Instant.parse("2026-11-22T03:04:05Z"), padded.occurredAt());
    }

}
