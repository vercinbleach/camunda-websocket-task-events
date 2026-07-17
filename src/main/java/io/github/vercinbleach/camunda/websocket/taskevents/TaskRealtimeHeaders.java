package io.github.vercinbleach.camunda.websocket.taskevents;

import org.springframework.messaging.simp.stomp.StompHeaderAccessor;

import java.util.List;
import java.util.Map;

final class TaskRealtimeHeaders {
    private TaskRealtimeHeaders() {
    }

    static String bearerToken(StompHeaderAccessor accessor) {
        String authorization = firstNativeHeaderIgnoreCase(accessor, "Authorization");
        if (authorization == null || !authorization.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return null;
        }
        String token = authorization.substring(7).trim();
        return token.isEmpty() ? null : token;
    }

    static boolean hasCredentials(StompHeaderAccessor accessor) {
        return hasNonBlank(firstNativeHeaderIgnoreCase(accessor, "Authorization"))
                || hasNonBlank(accessor.getLogin())
                || hasNonBlank(accessor.getPasscode());
    }

    static String firstNativeHeaderIgnoreCase(StompHeaderAccessor accessor, String name) {
        Map<String, List<String>> headers = accessor.toNativeHeaderMap();
        if (headers == null) {
            return null;
        }
        return headers.entrySet().stream()
                .filter(entry -> entry.getKey().equalsIgnoreCase(name))
                .map(Map.Entry::getValue)
                .filter(values -> values != null && !values.isEmpty())
                .map(values -> values.get(0))
                .findFirst()
                .orElse(null);
    }

    private static boolean hasNonBlank(String value) {
        return value != null && !value.isBlank();
    }
}
