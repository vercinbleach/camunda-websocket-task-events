package io.github.vercinbleach.camunda.websocket.taskevents;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/**
 * Versioned, business-data-free realtime envelope.
 */
@JsonPropertyOrder({"schemaVersion", "type"})
public record TaskRealtimeEnvelope(
        int schemaVersion,
        TaskEventType type
) {
    public static final int CURRENT_SCHEMA_VERSION = 1;
}
