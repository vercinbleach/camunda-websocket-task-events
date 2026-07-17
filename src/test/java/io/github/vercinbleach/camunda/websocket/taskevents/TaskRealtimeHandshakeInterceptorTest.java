package io.github.vercinbleach.camunda.websocket.taskevents;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.socket.WebSocketHandler;

import java.security.Principal;
import java.util.HashMap;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TaskRealtimeHandshakeInterceptorTest {
    @Test
    void trustsThePrincipalAlreadyEstablishedByHttpSecurity() {
        ServerHttpRequest request = mock(ServerHttpRequest.class);
        when(request.getPrincipal()).thenReturn(() -> "oauth-user");
        TaskRealtimeHandshakeAuthenticator never = mock(TaskRealtimeHandshakeAuthenticator.class);

        boolean accepted = new TaskRealtimeHandshakeInterceptor(List.of(never)).beforeHandshake(
                request, mock(ServerHttpResponse.class), mock(WebSocketHandler.class), new HashMap<>());

        assertThat(accepted).isTrue();
    }

    @Test
    void allowsDeliberatelyAnonymousHandshakeWhenNoCredentialWasSupplied() {
        ServerHttpRequest request = mock(ServerHttpRequest.class);
        when(request.getHeaders()).thenReturn(new HttpHeaders());

        assertThat(new TaskRealtimeHandshakeInterceptor(List.of()).beforeHandshake(
                request, mock(ServerHttpResponse.class), mock(WebSocketHandler.class), new HashMap<>()))
                .isTrue();
    }

    @Test
    void rejectsHttpAuthorizationThatNoConfiguredMechanismCanValidate() {
        ServerHttpRequest request = mock(ServerHttpRequest.class);
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth("unknown");
        when(request.getHeaders()).thenReturn(headers);
        ServerHttpResponse response = mock(ServerHttpResponse.class);

        assertThat(new TaskRealtimeHandshakeInterceptor(List.of()).beforeHandshake(
                request, response, mock(WebSocketHandler.class), new HashMap<>())).isFalse();
        org.mockito.Mockito.verify(response).setStatusCode(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void exposesAnAuthenticatedHandshakePrincipalToTheHandshakeHandler() {
        ServerHttpRequest request = mock(ServerHttpRequest.class);
        HttpHeaders headers = new HttpHeaders();
        headers.setBasicAuth("demo", "demo");
        when(request.getHeaders()).thenReturn(headers);
        Principal principal = () -> "demo";
        TaskRealtimeHandshakeAuthenticator authenticator = new TaskRealtimeHandshakeAuthenticator() {
            @Override
            public boolean supports(ServerHttpRequest ignored) {
                return true;
            }

            @Override
            public Principal authenticate(ServerHttpRequest ignored, ServerHttpResponse response) {
                return principal;
            }
        };
        HashMap<String, Object> attributes = new HashMap<>();

        assertThat(new TaskRealtimeHandshakeInterceptor(List.of(authenticator)).beforeHandshake(
                request, mock(ServerHttpResponse.class), mock(WebSocketHandler.class), attributes)).isTrue();
        assertThat(attributes.get(TaskRealtimeHandshakeInterceptor.AUTHENTICATED_PRINCIPAL_ATTRIBUTE))
                .isSameAs(principal);
    }

    @Test
    void higherPriorityCustomAuthenticationCanOverrideABroadAdapter() {
        ServerHttpRequest request = mock(ServerHttpRequest.class);
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth("custom-token");
        when(request.getHeaders()).thenReturn(headers);
        TaskRealtimeHandshakeAuthenticator custom = fixedAuthenticator(0, "custom-user");
        TaskRealtimeHandshakeAuthenticator broadCamundaLike = fixedAuthenticator(
                org.springframework.core.Ordered.LOWEST_PRECEDENCE - 100, "camunda-user");
        HashMap<String, Object> attributes = new HashMap<>();

        assertThat(new TaskRealtimeHandshakeInterceptor(List.of(broadCamundaLike, custom)).beforeHandshake(
                request, mock(ServerHttpResponse.class), mock(WebSocketHandler.class), attributes)).isTrue();
        assertThat(((Principal) attributes.get(
                TaskRealtimeHandshakeInterceptor.AUTHENTICATED_PRINCIPAL_ATTRIBUTE)).getName())
                .isEqualTo("custom-user");
    }

    private TaskRealtimeHandshakeAuthenticator fixedAuthenticator(int order, String username) {
        return new FixedHandshakeAuthenticator(order, username);
    }

    private record FixedHandshakeAuthenticator(int order, String username)
            implements TaskRealtimeHandshakeAuthenticator, org.springframework.core.Ordered {
        @Override
        public boolean supports(ServerHttpRequest request) {
            return true;
        }

        @Override
        public Principal authenticate(ServerHttpRequest request, ServerHttpResponse response) {
            return () -> username;
        }

        @Override
        public int getOrder() {
            return order;
        }
    }

}
