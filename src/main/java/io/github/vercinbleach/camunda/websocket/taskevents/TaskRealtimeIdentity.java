package io.github.vercinbleach.camunda.websocket.taskevents;

import java.security.Principal;
import java.time.Instant;

public record TaskRealtimeIdentity(Principal principal, Instant expiresAt) {
    public TaskRealtimeIdentity {
        if (principal == null || principal.getName() == null || principal.getName().isBlank()) {
            throw new IllegalArgumentException("A non-empty realtime principal is required");
        }
    }
}
