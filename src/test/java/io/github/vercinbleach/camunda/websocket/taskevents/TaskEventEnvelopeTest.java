package io.github.vercinbleach.camunda.websocket.taskevents;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TaskEventEnvelopeTest {
    @Test
    void serializesOnlyTheVersionedTaskEventFields() throws Exception {
        TaskRealtimeEnvelope envelope = new TaskRealtimeEnvelope(
                2,
                TaskEventType.TASK_EVENT,
                "task-123",
                TaskLifecycleEvent.ASSIGNMENT,
                "demo");

        Set<String> actualFields = new java.util.HashSet<>();
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        var node = mapper.readTree(mapper.writeValueAsString(envelope));
        node.fieldNames().forEachRemaining(actualFields::add);

        assertThat(actualFields).containsExactlyInAnyOrder(
                "schemaVersion", "type", "taskId", "eventType", "assignee");
        assertThat(node.get("schemaVersion").asInt()).isEqualTo(2);
        assertThat(node.get("type").asText()).isEqualTo("TASK_EVENT");
        assertThat(node.get("taskId").asText()).isEqualTo("task-123");
        assertThat(node.get("eventType").asText()).isEqualTo("assignment");
        assertThat(node.get("assignee").asText()).isEqualTo("demo");
    }

    @Test
    void omitsTheOptionalAssigneeWithoutAddingBusinessData() throws Exception {
        TaskRealtimeEnvelope envelope = new TaskRealtimeEnvelope(
                2,
                TaskEventType.TASK_EVENT,
                "task-123",
                TaskLifecycleEvent.CREATE,
                null);

        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        var node = mapper.readTree(mapper.writeValueAsString(envelope));

        assertThat(node.has("assignee")).isFalse();
        assertThat(node.fieldNames()).toIterable().containsExactly(
                "schemaVersion", "type", "taskId", "eventType");
    }

    @Test
    void serializesAccessInvalidationWithoutTaskMetadata() throws Exception {
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        var node = mapper.readTree(mapper.writeValueAsString(
                TaskRealtimeEnvelope.accessInvalidated()));

        assertThat(node.fieldNames()).toIterable().containsExactly(
                "schemaVersion", "type");
        assertThat(node.get("type").asText()).isEqualTo("TASKS_INVALIDATED");
    }

    @Test
    void reconcilesVisibilityWhenATimedOutTaskIsNoLongerReadable() {
        TaskRealtimeEnvelope envelope = new TaskRealtimeEnvelope(
                TaskRealtimeEnvelope.CURRENT_SCHEMA_VERSION,
                TaskEventType.TASK_EVENT,
                "task-123",
                TaskLifecycleEvent.TIMEOUT,
                null);

        assertThat(envelope.requiresVisibilityReconciliation()).isTrue();
    }

    @Test
    void rejectsAssigneeMetadataOutsideAssignmentEvents() {
        assertThatThrownBy(() -> new TaskRealtimeEnvelope(
                2,
                TaskEventType.TASK_EVENT,
                "task-123",
                TaskLifecycleEvent.UPDATE,
                "demo"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
