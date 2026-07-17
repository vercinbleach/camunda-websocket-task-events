package io.github.vercinbleach.camunda.websocket.taskevents;

import org.springframework.core.Ordered;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;

/**
 * Extension point for application-specific STOMP CONNECT authentication.
 * Standard Spring Security adapters are registered automatically when their beans exist.
 */
public interface TaskRealtimeCredentialAuthenticator extends Ordered {
    boolean supports(StompHeaderAccessor connectFrame);

    TaskRealtimeIdentity authenticate(StompHeaderAccessor connectFrame);

    @Override
    default int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }

}
