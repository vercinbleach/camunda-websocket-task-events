package io.github.vercinbleach.camunda.websocket.taskevents;

import java.util.Set;

public record TaskRealtimePublication(
        TaskRealtimeEnvelope envelope,
        Set<String> visibleRecipientsBeforeCommit
) {
    public TaskRealtimePublication {
        if (envelope == null) {
            throw new IllegalArgumentException("Realtime envelope must not be null");
        }
        visibleRecipientsBeforeCommit = visibleRecipientsBeforeCommit == null
                ? Set.of()
                : Set.copyOf(visibleRecipientsBeforeCommit);
    }

    public static TaskRealtimePublication withoutCapturedRecipients(TaskRealtimeEnvelope envelope) {
        return new TaskRealtimePublication(envelope, Set.of());
    }
}
