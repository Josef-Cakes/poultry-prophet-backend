package com.poultryprophet.event;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class EventTypeSchemaContractTest {

    @Test
    void schemaAllowsEveryEventTypeAcceptedByTheApi() throws IOException {
        String schema;
        try (var input = getClass().getResourceAsStream("/schema.sql")) {
            assertThat(input).as("schema.sql must be available on the classpath").isNotNull();
            schema = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }

        for (EventType eventType : EventType.values()) {
            assertThat(schema)
                    .as("schema.sql should allow event type %s", eventType.name())
                    .contains("'" + eventType.name() + "'");
        }
    }
}
