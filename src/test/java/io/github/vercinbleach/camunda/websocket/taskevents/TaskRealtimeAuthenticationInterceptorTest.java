package io.github.vercinbleach.camunda.websocket.taskevents;

import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.socket.CloseStatus;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TaskRealtimeAuthenticationInterceptorTest {
    @Test
    void delegatesToTheSelectedProviderAndSetsItsPrincipal() {
        RealtimeProperties properties = properties("custom");
        TaskRealtimeAuthenticationProvider provider = provider("custom");
        TaskSessionRegistry sessionRegistry = mock(TaskSessionRegistry.class);
        TaskRealtimeMetrics metrics = mock(TaskRealtimeMetrics.class);
        Instant expiry = Instant.parse("2026-07-14T10:01:00Z");
        when(provider.authenticate(org.mockito.ArgumentMatchers.any())).thenReturn(new TaskRealtimeAuthentication(
                UsernamePasswordAuthenticationToken.authenticated("demo", null, List.of()),
                expiry));
        when(sessionRegistry.authenticate("session-1", expiry)).thenReturn(true);
        TaskRealtimeAuthenticationInterceptor interceptor = new TaskRealtimeAuthenticationInterceptor(
                properties, List.of(provider), sessionRegistry, metrics);
        Message<byte[]> connect = connect();

        Message<?> result = interceptor.preSend(connect, mock(MessageChannel.class));

        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(result, StompHeaderAccessor.class);
        assertThat(accessor).isNotNull();
        assertThat(accessor.getUser()).isNotNull();
        assertThat(accessor.getUser().getName()).isEqualTo("demo");
    }

    @Test
    void acceptsAProviderWithoutCredentialExpiry() {
        RealtimeProperties properties = properties(HttpPrincipalAuthenticationProvider.ID);
        TaskRealtimeAuthenticationProvider provider = provider(HttpPrincipalAuthenticationProvider.ID);
        TaskSessionRegistry sessionRegistry = mock(TaskSessionRegistry.class);
        when(provider.authenticate(org.mockito.ArgumentMatchers.any())).thenReturn(
                TaskRealtimeAuthentication.withoutExpiry(
                        UsernamePasswordAuthenticationToken.authenticated("demo", null, List.of())));
        when(sessionRegistry.authenticate("session-1", null)).thenReturn(true);
        TaskRealtimeAuthenticationInterceptor interceptor = new TaskRealtimeAuthenticationInterceptor(
                properties, List.of(provider), sessionRegistry, mock(TaskRealtimeMetrics.class));

        assertThat(interceptor.preSend(connect(), mock(MessageChannel.class))).isNotNull();
    }

    @Test
    void failsFastWhenTheConfiguredProviderDoesNotExist() {
        RealtimeProperties properties = properties("missing");

        assertThatThrownBy(() -> new TaskRealtimeAuthenticationInterceptor(
                properties,
                List.of(provider("another")),
                mock(TaskSessionRegistry.class),
                mock(TaskRealtimeMetrics.class)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("missing");
    }

    @Test
    void rejectsProviderFailureWithoutLeakingCredentials() {
        RealtimeProperties properties = properties("custom");
        TaskRealtimeAuthenticationProvider provider = provider("custom");
        TaskSessionRegistry sessionRegistry = mock(TaskSessionRegistry.class);
        TaskRealtimeMetrics metrics = mock(TaskRealtimeMetrics.class);
        when(provider.authenticate(org.mockito.ArgumentMatchers.any()))
                .thenThrow(new IllegalArgumentException("secret-token"));
        TaskRealtimeAuthenticationInterceptor interceptor = new TaskRealtimeAuthenticationInterceptor(
                properties, List.of(provider), sessionRegistry, metrics);

        assertThatThrownBy(() -> interceptor.preSend(connect(), mock(MessageChannel.class)))
                .isInstanceOf(MessageDeliveryException.class)
                .hasMessageNotContaining("secret-token");
        verify(sessionRegistry).close(eq("session-1"), eq(CloseStatus.POLICY_VIOLATION));
        verify(metrics).recordRejectedAuthentication();
    }

    private RealtimeProperties properties(String provider) {
        RealtimeProperties properties = new RealtimeProperties();
        properties.getAuthentication().setProvider(provider);
        return properties;
    }

    private TaskRealtimeAuthenticationProvider provider(String id) {
        TaskRealtimeAuthenticationProvider provider = mock(TaskRealtimeAuthenticationProvider.class);
        when(provider.id()).thenReturn(id);
        return provider;
    }

    private Message<byte[]> connect() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setSessionId("session-1");
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }
}
