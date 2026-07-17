package io.github.vercinbleach.camunda.websocket.taskevents;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class TaskEventEnvelopeTest {
    @Test
    void serializesOnlyTheVersionedRealtimeFields() throws Exception {
        TaskRealtimeEnvelope envelope = new TaskRealtimeEnvelope(
                1,
                TaskEventType.TASKS_INVALIDATED);

        Set<String> actualFields = new java.util.HashSet<>();
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        var node = mapper.readTree(mapper.writeValueAsString(envelope));
        node.fieldNames().forEachRemaining(actualFields::add);

        assertThat(actualFields).containsExactlyInAnyOrder("schemaVersion", "type");
        assertThat(node.has("taskId")).isFalse();
        assertThat(node.has("assignee")).isFalse();
        assertThat(node.get("type").asText()).isEqualTo("TASKS_INVALIDATED");
    }
}
