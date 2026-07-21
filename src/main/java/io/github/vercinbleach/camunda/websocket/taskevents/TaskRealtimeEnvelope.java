package io.github.vercinbleach.camunda.websocket.taskevents;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonIgnore;
import org.camunda.bpm.spring.boot.starter.event.TaskEvent;

import java.util.Optional;

/**
 * Versioned task lifecycle notification. REST remains the source of truth.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({"schemaVersion", "type", "taskId", "eventType"})
public record TaskRealtimeEnvelope(
        int schemaVersion,
        TaskEventType type,
        String taskId,
        TaskLifecycleEvent eventType
) {
    public static final int CURRENT_SCHEMA_VERSION = 3;

    public TaskRealtimeEnvelope {
        if (schemaVersion != CURRENT_SCHEMA_VERSION) {
            throw new IllegalArgumentException("Unsupported task realtime schema version");
        }
        if (type == TaskEventType.TASK_UPSERT || type == TaskEventType.TASK_REMOVE) {
            if (taskId == null || taskId.isBlank()) {
                throw new IllegalArgumentException("Task id must not be blank");
            }
            if (eventType == null) {
                throw new IllegalArgumentException("Task event type must not be null");
            }
            if (type == TaskEventType.TASK_UPSERT && !eventType.isUpsert()) {
                throw new IllegalArgumentException("Task upsert must use create, assignment, or update");
            }
            if (type == TaskEventType.TASK_REMOVE && eventType.isUpsert()) {
                throw new IllegalArgumentException("Task remove must use complete or delete");
            }
        } else if (type == TaskEventType.TASKS_INVALIDATED) {
            if (taskId != null || eventType != null) {
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
                        eventType.isUpsert() ? TaskEventType.TASK_UPSERT : TaskEventType.TASK_REMOVE,
                        event.getId(),
                        eventType));
    }

    public static TaskRealtimeEnvelope accessInvalidated() {
        return new TaskRealtimeEnvelope(
                CURRENT_SCHEMA_VERSION,
                TaskEventType.TASKS_INVALIDATED,
                null,
                null);
    }

    public boolean requiresPostCommitVisibilityCapture() {
        return type == TaskEventType.TASK_UPSERT;
    }

    @JsonIgnore
    public boolean isRemove() {
        return type == TaskEventType.TASK_REMOVE;
    }

}
