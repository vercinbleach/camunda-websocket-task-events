package io.github.vercinbleach.camunda.websocket.taskevents;

import org.springframework.core.Ordered;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;

import java.security.Principal;

/** Extension point for credentials carried by the HTTP WebSocket handshake. */
public interface TaskRealtimeHandshakeAuthenticator extends Ordered {
    boolean supports(ServerHttpRequest request);

    Principal authenticate(ServerHttpRequest request, ServerHttpResponse response);

    @Override
    default int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }

}
