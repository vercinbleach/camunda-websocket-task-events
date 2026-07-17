package io.github.vercinbleach.camunda.websocket.taskevents;

import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;

@Component
public class TaskSessionLimitsInterceptor implements ChannelInterceptor {
    private final TaskSessionRegistry sessionRegistry;
    private final TaskRealtimeMetrics metrics;

    public TaskSessionLimitsInterceptor(TaskSessionRegistry sessionRegistry, TaskRealtimeMetrics metrics) {
        this.sessionRegistry = sessionRegistry;
        this.metrics = metrics;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() == null) {
            return message;
        }

        String sessionId = accessor.getSessionId();
        if (accessor.getCommand() == StompCommand.SUBSCRIBE) {
            if (!sessionRegistry.registerSubscription(sessionId, accessor.getSubscriptionId(), accessor.getDestination())) {
                metrics.recordRejectedSubscription();
                sessionRegistry.close(sessionId, org.springframework.web.socket.CloseStatus.POLICY_VIOLATION);
                throw new MessageDeliveryException("STOMP subscription rejected");
            }
        } else if (accessor.getCommand() == StompCommand.UNSUBSCRIBE) {
            if (!sessionRegistry.unregisterSubscription(sessionId, accessor.getSubscriptionId())) {
                metrics.recordRejectedSubscription();
                throw new MessageDeliveryException("STOMP unsubscription rejected");
            }
        }
        return message;
    }
}
