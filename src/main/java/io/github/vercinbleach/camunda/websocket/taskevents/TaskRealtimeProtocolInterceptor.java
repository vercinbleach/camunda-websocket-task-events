package io.github.vercinbleach.camunda.websocket.taskevents;

import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

@Component
public class TaskRealtimeProtocolInterceptor implements ChannelInterceptor {
    static final String TASK_DESTINATION = "/user/queue/task-events";

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null) {
            throw new MessageDeliveryException("STOMP frame rejected");
        }
        if (accessor.getMessageType() == SimpMessageType.HEARTBEAT) {
            return message;
        }

        StompCommand command = accessor.getCommand();
        boolean allowed = command != null && switch (command) {
            case CONNECT, DISCONNECT, UNSUBSCRIBE -> true;
            case SUBSCRIBE -> TASK_DESTINATION.equals(accessor.getDestination());
            default -> false;
        };
        if (!allowed) {
            throw new MessageDeliveryException("STOMP frame rejected");
        }
        return message;
    }
}
