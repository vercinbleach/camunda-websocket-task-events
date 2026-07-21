package io.github.vercinbleach.camunda.websocket.taskevents;

import java.util.Set;

public record TaskRealtimePublication(
        TaskRealtimeEnvelope envelope,
        Set<String> visibleRecipients,
        String capturedRemoveAssignee
) {
    public TaskRealtimePublication {
        if (envelope == null) {
            throw new IllegalArgumentException("Realtime envelope must not be null");
        }
        visibleRecipients = visibleRecipients == null
                ? Set.of()
                : Set.copyOf(visibleRecipients);
        if (!envelope.isRemove() && capturedRemoveAssignee != null) {
            throw new IllegalArgumentException("Only task removals may capture an assignee");
        }
    }

    public static TaskRealtimePublication capture(TaskRealtimeEnvelope envelope, String assignee) {
        String removeAssignee = envelope.isRemove() ? assignee : null;
        return new TaskRealtimePublication(envelope, Set.of(), removeAssignee);
    }

    public TaskRealtimePublication withVisibleRecipients(Set<String> recipients) {
        return new TaskRealtimePublication(envelope, recipients, capturedRemoveAssignee);
    }
}
