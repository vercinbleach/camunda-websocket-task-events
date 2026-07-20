package io.github.vercinbleach.camunda.websocket.taskevents;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.annotation.JsonInclude;
import org.camunda.bpm.spring.boot.starter.event.TaskEvent;

import java.util.Optional;

/**
 * Versioned task lifecycle notification. REST remains the source of truth.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({"schemaVersion", "type", "taskId", "eventType", "assignee"})
public record TaskRealtimeEnvelope(
        int schemaVersion,
        TaskEventType type,
        String taskId,
        TaskLifecycleEvent eventType,
        String assignee
) {
    public static final int CURRENT_SCHEMA_VERSION = 2;

    public TaskRealtimeEnvelope {
        if (schemaVersion != CURRENT_SCHEMA_VERSION) {
            throw new IllegalArgumentException("Unsupported task realtime schema version");
        }
        if (type == TaskEventType.TASK_EVENT) {
            if (taskId == null || taskId.isBlank()) {
                throw new IllegalArgumentException("Task id must not be blank");
            }
            if (eventType == null) {
                throw new IllegalArgumentException("Task event type must not be null");
            }
            if (eventType != TaskLifecycleEvent.ASSIGNMENT && assignee != null) {
                throw new IllegalArgumentException("Assignee is only allowed on assignment events");
            }
        } else if (type == TaskEventType.TASKS_INVALIDATED) {
            if (taskId != null || eventType != null || assignee != null) {
                throw new IllegalArgumentException("Access invalidation must not contain task metadata");
            }
        } else {
            throw new IllegalArgumentException("Unsupported task realtime envelope type");
        }
    }

    public static Optional<TaskRealtimeEnvelope> from(TaskEvent event) {
        if (event == null || event.getId() == null || event.getId().isBlank()) {
            return Optional.empty();
        }
        return TaskLifecycleEvent.fromCamundaEventName(event.getEventName())
                .map(eventType -> new TaskRealtimeEnvelope(
                        CURRENT_SCHEMA_VERSION,
                        TaskEventType.TASK_EVENT,
                        event.getId(),
                        eventType,
                        eventType == TaskLifecycleEvent.ASSIGNMENT ? event.getAssignee() : null));
    }

    public static TaskRealtimeEnvelope accessInvalidated() {
        return new TaskRealtimeEnvelope(
                CURRENT_SCHEMA_VERSION,
                TaskEventType.TASKS_INVALIDATED,
                null,
                null,
                null);
    }

    public boolean requiresVisibilityReconciliation() {
        return type == TaskEventType.TASK_EVENT
                && (eventType == TaskLifecycleEvent.ASSIGNMENT
                || eventType == TaskLifecycleEvent.UPDATE
                || eventType == TaskLifecycleEvent.COMPLETE
                || eventType == TaskLifecycleEvent.DELETE
                || eventType == TaskLifecycleEvent.TIMEOUT);
    }

    public boolean requiresPostCommitVisibilityCapture() {
        return type == TaskEventType.TASK_EVENT
                && (eventType == TaskLifecycleEvent.CREATE
                || eventType == TaskLifecycleEvent.ASSIGNMENT
                || eventType == TaskLifecycleEvent.UPDATE);
    }

}
