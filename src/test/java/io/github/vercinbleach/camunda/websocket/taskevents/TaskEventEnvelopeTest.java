package io.github.vercinbleach.camunda.websocket.taskevents;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TaskEventEnvelopeTest {
    @Test
    void serializesVersionThreeUpsertsWithoutAssigneeMetadata() throws Exception {
        TaskRealtimeEnvelope envelope = new TaskRealtimeEnvelope(
                TaskRealtimeEnvelope.CURRENT_SCHEMA_VERSION,
                TaskEventType.TASK_UPSERT,
                "task-123",
                TaskLifecycleEvent.ASSIGNMENT);

        Set<String> actualFields = new java.util.HashSet<>();
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        var node = mapper.readTree(mapper.writeValueAsString(envelope));
        node.fieldNames().forEachRemaining(actualFields::add);

        assertThat(actualFields).containsExactlyInAnyOrder(
                "schemaVersion", "type", "taskId", "eventType");
        assertThat(node.get("schemaVersion").asInt()).isEqualTo(3);
        assertThat(node.get("type").asText()).isEqualTo("TASK_UPSERT");
        assertThat(node.get("taskId").asText()).isEqualTo("task-123");
        assertThat(node.get("eventType").asText()).isEqualTo("assignment");
    }

    @Test
    void serializesTaskRemovalWithOnlyItsPublicLifecycleMetadata() throws Exception {
        TaskRealtimeEnvelope envelope = new TaskRealtimeEnvelope(
                TaskRealtimeEnvelope.CURRENT_SCHEMA_VERSION,
                TaskEventType.TASK_REMOVE,
                "task-123",
                TaskLifecycleEvent.COMPLETE);

        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        var node = mapper.readTree(mapper.writeValueAsString(envelope));

        assertThat(node.fieldNames()).toIterable().containsExactly(
                "schemaVersion", "type", "taskId", "eventType");
        assertThat(node.get("type").asText()).isEqualTo("TASK_REMOVE");
        assertThat(node.get("eventType").asText()).isEqualTo("complete");
    }

    @Test
    void serializesAccessInvalidationWithoutTaskMetadata() throws Exception {
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        var node = mapper.readTree(mapper.writeValueAsString(
                TaskRealtimeEnvelope.accessInvalidated()));

        assertThat(node.fieldNames()).toIterable().containsExactly(
                "schemaVersion", "type");
        assertThat(node.get("schemaVersion").asInt()).isEqualTo(3);
        assertThat(node.get("type").asText()).isEqualTo("TASKS_INVALIDATED");
    }

    @Test
    void rejectsLifecycleEventsMappedToTheWrongEnvelopeType() {
        assertThatThrownBy(() -> new TaskRealtimeEnvelope(
                TaskRealtimeEnvelope.CURRENT_SCHEMA_VERSION,
                TaskEventType.TASK_UPSERT,
                "task-123",
                TaskLifecycleEvent.COMPLETE))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TaskRealtimeEnvelope(
                TaskRealtimeEnvelope.CURRENT_SCHEMA_VERSION,
                TaskEventType.TASK_REMOVE,
                "task-123",
                TaskLifecycleEvent.UPDATE))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
