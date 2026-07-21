package io.github.vercinbleach.camunda.websocket.taskevents;

import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Arrays;
import java.util.Optional;

public enum TaskLifecycleEvent {
    // Camunda 7.24's Spring EventPublisherPlugin does not publish TaskListener timeout events.
    CREATE("create"),
    ASSIGNMENT("assignment"),
    UPDATE("update"),
    COMPLETE("complete"),
    DELETE("delete");

    private final String value;

    TaskLifecycleEvent(String value) {
        this.value = value;
    }

    @JsonValue
    public String value() {
        return value;
    }

    public boolean isUpsert() {
        return this == CREATE || this == ASSIGNMENT || this == UPDATE;
    }

    public static Optional<TaskLifecycleEvent> fromCamundaEventName(String eventName) {
        return Arrays.stream(values())
                .filter(value -> value.value.equals(eventName))
                .findFirst();
    }
}
