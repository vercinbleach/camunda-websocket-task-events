package io.github.vercinbleach.camunda.websocket.taskevents;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.web.socket.WebSocketSession;

import java.security.Principal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TaskRealtimeCredentialAuthenticationInterceptorTest {
    private TaskSessionRegistry registry;

    @AfterEach
    void tearDown() {
        if (registry != null) {
            registry.shutdown();
        }
    }

    @Test
    void keepsHandshakeIdentityWhenConnectHasNoCredentials() {
        TaskRealtimeCredentialAuthenticationInterceptor interceptor = interceptor(List.of());
        Message<byte[]> message = connect(null);

        assertThat(interceptor.preSend(message, mock(org.springframework.messaging.MessageChannel.class)))
                .isSameAs(message);
    }

    @Test
    void rejectsSuppliedCredentialsWhenNoAuthenticatorSupportsThem() {
        TaskRealtimeCredentialAuthenticationInterceptor interceptor = interceptor(List.of());

        assertThatThrownBy(() -> interceptor.preSend(
                connect("Bearer unhandled"), mock(org.springframework.messaging.MessageChannel.class)))
                .isInstanceOf(MessageDeliveryException.class)
                .hasMessage("STOMP CONNECT rejected");
    }

    @Test
    void authenticatesWithTheHighestPrioritySupportingAuthenticator() {
        Principal principal = () -> "demo";
        TaskRealtimeCredentialAuthenticator authenticator = new FixedAuthenticator(10, principal);
        TaskRealtimeCredentialAuthenticationInterceptor interceptor = interceptor(List.of(authenticator));
        Message<byte[]> message = connect("Bearer valid");

        interceptor.preSend(message, mock(org.springframework.messaging.MessageChannel.class));

        assertThat(StompHeaderAccessor.wrap(message).getUser()).isSameAs(principal);
    }

    @Test
    void rejectsAmbiguousAuthenticatorsInsteadOfTryingOne() {
        TaskRealtimeCredentialAuthenticationInterceptor interceptor = interceptor(List.of(
                new FixedAuthenticator(100, () -> "jwt"),
                new FixedAuthenticator(100, () -> "opaque")));

        assertThatThrownBy(() -> interceptor.preSend(
                connect("Bearer ambiguous"), mock(org.springframework.messaging.MessageChannel.class)))
                .isInstanceOf(MessageDeliveryException.class);
    }

    private TaskRealtimeCredentialAuthenticationInterceptor interceptor(
            List<TaskRealtimeCredentialAuthenticator> authenticators) {
        RealtimeProperties properties = new RealtimeProperties();
        registry = new TaskSessionRegistry(properties);
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn("session-1");
        when(session.isOpen()).thenReturn(true);
        assertThat(registry.registerTransportSession(session)).isTrue();
        return new TaskRealtimeCredentialAuthenticationInterceptor(
                authenticators, registry, new TaskRealtimeMetrics());
    }

    private Message<byte[]> connect(String authorization) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setSessionId("session-1");
        if (authorization != null) {
            accessor.addNativeHeader("Authorization", authorization);
        }
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    private record FixedAuthenticator(int order, Principal principal)
            implements TaskRealtimeCredentialAuthenticator, org.springframework.core.Ordered {
        @Override
        public boolean supports(StompHeaderAccessor connectFrame) {
            return TaskRealtimeHeaders.bearerToken(connectFrame) != null;
        }

        @Override
        public TaskRealtimeIdentity authenticate(StompHeaderAccessor connectFrame) {
            return new TaskRealtimeIdentity(principal, null);
        }

        @Override
        public int getOrder() {
            return order;
        }
    }
}
