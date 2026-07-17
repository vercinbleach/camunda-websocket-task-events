package io.github.vercinbleach.camunda.websocket.taskevents;

import org.springframework.messaging.Message;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.core.Authentication;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;

@Component
public class TaskStompAuthorizationManager implements AuthorizationManager<Message<?>> {
    private static final String TASK_DESTINATION = "/user/queue/task-events";

    @Override
    public AuthorizationDecision check(Supplier<Authentication> authenticationSupplier, Message<?> message) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null) {
            return new AuthorizationDecision(false);
        }

        Authentication authentication = authenticationSupplier.get();
        if ((authentication == null || !authentication.isAuthenticated()) && accessor.getUser() instanceof Authentication messageAuthentication) {
            authentication = messageAuthentication;
        }
        boolean authenticated = authentication != null
                && authentication.isAuthenticated()
                && authentication.getName() != null
                && !authentication.getName().isBlank();
        if (!authenticated) {
            return new AuthorizationDecision(false);
        }

        if (accessor.getMessageType() == SimpMessageType.HEARTBEAT) {
            return new AuthorizationDecision(true);
        }
        if (accessor.getCommand() == null) {
            return new AuthorizationDecision(false);
        }

        boolean allowed = switch (accessor.getCommand()) {
            case CONNECT, DISCONNECT, UNSUBSCRIBE -> true;
            case SUBSCRIBE -> TASK_DESTINATION.equals(accessor.getDestination());
            default -> false;
        };
        return new AuthorizationDecision(allowed);
    }
}
