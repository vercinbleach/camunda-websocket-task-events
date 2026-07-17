package io.github.vercinbleach.camunda.websocket.taskevents;

import org.springframework.security.core.Authentication;

import java.time.Instant;
import java.util.Objects;

public record TaskRealtimeAuthentication(Authentication principal, Instant expiresAt) {
    public TaskRealtimeAuthentication {
        Objects.requireNonNull(principal, "principal");
        if (!principal.isAuthenticated() || principal.getName() == null || principal.getName().isBlank()) {
            throw new IllegalArgumentException("realtime principal must be authenticated and named");
        }
    }

    public static TaskRealtimeAuthentication withoutExpiry(Authentication principal) {
        return new TaskRealtimeAuthentication(principal, null);
    }
}
