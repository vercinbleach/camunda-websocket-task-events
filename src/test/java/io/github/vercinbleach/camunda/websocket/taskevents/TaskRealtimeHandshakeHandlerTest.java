package io.github.vercinbleach.camunda.websocket.taskevents;

import org.junit.jupiter.api.Test;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.web.socket.WebSocketHandler;

import java.security.Principal;
import java.util.HashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TaskRealtimeHandshakeHandlerTest {
    private final TaskRealtimeHandshakeHandler handshakeHandler = new TaskRealtimeHandshakeHandler();
    private final WebSocketHandler webSocketHandler = mock(WebSocketHandler.class);

    @Test
    void preservesThePrincipalEstablishedByHttpSecurity() {
        Principal existing = () -> "demo";
        ServerHttpRequest request = mock(ServerHttpRequest.class);
        when(request.getPrincipal()).thenReturn(existing);

        assertThat(handshakeHandler.determineUser(request, webSocketHandler, new HashMap<>()))
                .isSameAs(existing);
    }

    @Test
    void createsAnEphemeralRoutingPrincipalWhenHttpAllowsAnonymousAccess() {
        ServerHttpRequest request = mock(ServerHttpRequest.class);

        Principal first = handshakeHandler.determineUser(request, webSocketHandler, new HashMap<>());
        Principal second = handshakeHandler.determineUser(request, webSocketHandler, new HashMap<>());

        assertThat(first.getName()).startsWith("task-events-session:");
        assertThat(second.getName()).startsWith("task-events-session:");
        assertThat(first.getName()).isNotEqualTo(second.getName());
    }

    @Test
    void usesThePrincipalAuthenticatedByAHandshakeAdapter() {
        ServerHttpRequest request = mock(ServerHttpRequest.class);
        Principal authenticated = () -> "camunda-basic-user";
        HashMap<String, Object> attributes = new HashMap<>();
        attributes.put(TaskRealtimeHandshakeInterceptor.AUTHENTICATED_PRINCIPAL_ATTRIBUTE, authenticated);

        assertThat(handshakeHandler.determineUser(request, webSocketHandler, attributes))
                .isSameAs(authenticated);
    }
}
