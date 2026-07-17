package io.github.vercinbleach.camunda.websocket.taskevents;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.security.Principal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class TaskRealtimeHandshakeInterceptor implements HandshakeInterceptor {
    static final String AUTHENTICATED_PRINCIPAL_ATTRIBUTE =
            TaskRealtimeHandshakeInterceptor.class.getName() + ".principal";

    private final List<TaskRealtimeHandshakeAuthenticator> authenticators;

    public TaskRealtimeHandshakeInterceptor(List<TaskRealtimeHandshakeAuthenticator> authenticators) {
        this.authenticators = List.copyOf(authenticators);
    }

    @Override
    public boolean beforeHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Map<String, Object> attributes) {
        Principal existing = request.getPrincipal();
        if (existing != null && existing.getName() != null && !existing.getName().isBlank()) {
            return true;
        }

        List<TaskRealtimeHandshakeAuthenticator> supported;
        try {
            supported = new ArrayList<>(authenticators.stream()
                    .filter(authenticator -> authenticator.supports(request))
                    .toList());
            TaskRealtimeOrder.sort(supported);
        } catch (RuntimeException exception) {
            response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR);
            return false;
        }
        if (supported.isEmpty()) {
            if (hasAuthorization(request)) {
                response.setStatusCode(HttpStatus.UNAUTHORIZED);
                return false;
            }
            return true;
        }
        TaskRealtimeHandshakeAuthenticator selected = supported.get(0);
        if (supported.size() > 1
                && TaskRealtimeOrder.value(supported.get(1)) == TaskRealtimeOrder.value(selected)) {
            response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR);
            return false;
        }
        try {
            Principal authenticated = selected.authenticate(request, response);
            if (authenticated == null || authenticated.getName() == null || authenticated.getName().isBlank()) {
                response.setStatusCode(HttpStatus.UNAUTHORIZED);
                return false;
            }
            attributes.put(AUTHENTICATED_PRINCIPAL_ATTRIBUTE, authenticated);
            return true;
        } catch (RuntimeException exception) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }
    }

    @Override
    public void afterHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Exception exception) {
    }

    private boolean hasAuthorization(ServerHttpRequest request) {
        String authorization = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        return authorization != null && !authorization.isBlank();
    }
}
