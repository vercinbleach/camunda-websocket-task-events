package io.github.vercinbleach.camunda.websocket.taskevents;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.security.Principal;
import java.util.List;

@Component
@ConditionalOnProperty(prefix = "camunda.websocket.task-events.authentication", name = "provider", havingValue = HttpPrincipalAuthenticationProvider.ID, matchIfMissing = true)
public class HttpPrincipalAuthenticationProvider implements TaskRealtimeAuthenticationProvider {
    public static final String ID = "http-principal";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public TaskRealtimeAuthentication authenticate(StompHeaderAccessor connectHeaders) {
        Principal principal = connectHeaders.getUser();
        if (principal == null || principal.getName() == null || principal.getName().isBlank()) {
            throw new IllegalArgumentException("authenticated HTTP handshake principal required");
        }
        if (principal instanceof Authentication authentication) {
            return TaskRealtimeAuthentication.withoutExpiry(authentication);
        }
        return TaskRealtimeAuthentication.withoutExpiry(
                UsernamePasswordAuthenticationToken.authenticated(principal.getName(), null, List.of()));
    }
}
