package io.github.vercinbleach.camunda.websocket.taskevents;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;

import java.util.List;

@Component
public class TaskRealtimeAuthenticationInterceptor implements ChannelInterceptor {
    private static final Logger logger = LoggerFactory.getLogger(TaskRealtimeAuthenticationInterceptor.class);

    private final TaskRealtimeAuthenticationProvider authenticationProvider;
    private final TaskSessionRegistry sessionRegistry;
    private final TaskRealtimeMetrics metrics;

    public TaskRealtimeAuthenticationInterceptor(
            RealtimeProperties properties,
            List<TaskRealtimeAuthenticationProvider> authenticationProviders,
            TaskSessionRegistry sessionRegistry,
            TaskRealtimeMetrics metrics) {
        String selectedProvider = properties.getAuthentication().getProvider();
        List<TaskRealtimeAuthenticationProvider> matches = authenticationProviders.stream()
                .filter(provider -> selectedProvider.equals(provider.id()))
                .toList();
        if (matches.size() != 1) {
            throw new IllegalStateException("Expected exactly one realtime authentication provider named " + selectedProvider);
        }
        this.authenticationProvider = matches.get(0);
        this.sessionRegistry = sessionRegistry;
        this.metrics = metrics;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() != StompCommand.CONNECT) {
            return message;
        }

        String sessionId = accessor.getSessionId();
        try {
            TaskRealtimeAuthentication result = authenticationProvider.authenticate(accessor);
            if (!sessionRegistry.authenticate(sessionId, result.expiresAt())) {
                throw new IllegalArgumentException("session authentication rejected");
            }
            accessor.setUser(result.principal());
            return message;
        } catch (RuntimeException exception) {
            return reject(sessionId);
        }
    }

    private Message<?> reject(String sessionId) {
        metrics.recordRejectedAuthentication();
        if (sessionId != null) {
            sessionRegistry.close(sessionId, CloseStatus.POLICY_VIOLATION);
        }
        logger.debug("STOMP CONNECT rejected without credential details");
        throw new MessageDeliveryException("STOMP CONNECT rejected");
    }
}
