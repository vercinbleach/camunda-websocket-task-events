package io.github.vercinbleach.camunda.websocket.taskevents;

import org.springframework.http.server.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.support.DefaultHandshakeHandler;

import java.security.Principal;
import java.util.Map;
import java.util.UUID;

@Component
public class TaskRealtimeHandshakeHandler extends DefaultHandshakeHandler {
    private static final String ANONYMOUS_PREFIX = "task-events-session:";

    @Override
    protected Principal determineUser(
            ServerHttpRequest request,
            WebSocketHandler webSocketHandler,
            Map<String, Object> attributes) {
        Principal principal = request.getPrincipal();
        if (principal != null && principal.getName() != null && !principal.getName().isBlank()) {
            return principal;
        }
        return () -> ANONYMOUS_PREFIX + UUID.randomUUID();
    }
}
