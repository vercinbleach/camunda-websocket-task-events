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
import org.springframework.web.socket.CloseStatus;

import java.util.ArrayList;
import java.util.List;

public class TaskRealtimeCredentialAuthenticationInterceptor implements ChannelInterceptor {
    private static final Logger logger = LoggerFactory.getLogger(TaskRealtimeCredentialAuthenticationInterceptor.class);

    private final List<TaskRealtimeCredentialAuthenticator> authenticators;
    private final TaskSessionRegistry sessionRegistry;
    private final TaskRealtimeMetrics metrics;

    public TaskRealtimeCredentialAuthenticationInterceptor(
            List<TaskRealtimeCredentialAuthenticator> authenticators,
            TaskSessionRegistry sessionRegistry,
            TaskRealtimeMetrics metrics) {
        this.authenticators = List.copyOf(authenticators);
        this.sessionRegistry = sessionRegistry;
        this.metrics = metrics;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() != StompCommand.CONNECT) {
            return message;
        }

        List<TaskRealtimeCredentialAuthenticator> supported;
        try {
            supported = new ArrayList<>(authenticators.stream()
                    .filter(authenticator -> authenticator.supports(accessor))
                    .toList());
            TaskRealtimeOrder.sort(supported);
        } catch (RuntimeException exception) {
            return reject(accessor.getSessionId(), "Realtime authentication resolution failed");
        }
        if (supported.isEmpty()) {
            if (TaskRealtimeHeaders.hasCredentials(accessor)) {
                return reject(accessor.getSessionId(), "No authenticator accepts the supplied credentials");
            }
            return message;
        }

        TaskRealtimeCredentialAuthenticator selected = supported.get(0);
        if (supported.size() > 1
                && TaskRealtimeOrder.value(supported.get(1)) == TaskRealtimeOrder.value(selected)) {
            return reject(accessor.getSessionId(), "Realtime authentication is ambiguous");
        }

        try {
            TaskRealtimeIdentity identity = selected.authenticate(accessor);
            if (!sessionRegistry.bindCredentialExpiry(accessor.getSessionId(), identity.expiresAt())) {
                return reject(accessor.getSessionId(), "Realtime session authentication was rejected");
            }
            accessor.setUser(identity.principal());
            return message;
        } catch (RuntimeException exception) {
            return reject(accessor.getSessionId(), "STOMP CONNECT authentication failed");
        }
    }

    private Message<?> reject(String sessionId, String reason) {
        metrics.recordRejectedAuthentication();
        if (sessionId != null) {
            sessionRegistry.close(sessionId, CloseStatus.POLICY_VIOLATION);
        }
        logger.debug("{} without credential details", reason);
        throw new MessageDeliveryException("STOMP CONNECT rejected");
    }
}
