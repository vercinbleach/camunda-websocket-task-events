package io.github.vercinbleach.camunda.websocket.taskevents;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.AbstractOAuth2TokenAuthenticationToken;
import org.springframework.security.oauth2.server.resource.authentication.BearerTokenAuthenticationToken;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.core.Ordered;

import java.time.Instant;

final class SpringSecurityCredentialAuthenticator implements TaskRealtimeCredentialAuthenticator, Ordered {
    static final int RESOURCE_SERVER_ORDER = 100;

    private final AuthenticationManager authenticationManager;
    private final int order;

    SpringSecurityCredentialAuthenticator(
            AuthenticationManager authenticationManager,
            int order) {
        this.authenticationManager = authenticationManager;
        this.order = order;
    }

    @Override
    public boolean supports(StompHeaderAccessor connectFrame) {
        return TaskRealtimeHeaders.bearerToken(connectFrame) != null;
    }

    @Override
    public TaskRealtimeIdentity authenticate(StompHeaderAccessor connectFrame) {
        Authentication request = new BearerTokenAuthenticationToken(
                TaskRealtimeHeaders.bearerToken(connectFrame));
        Authentication result = authenticationManager.authenticate(request);
        if (result == null || !result.isAuthenticated()) {
            throw new IllegalArgumentException("Authentication manager did not authenticate STOMP CONNECT");
        }
        Instant expiresAt = result instanceof AbstractOAuth2TokenAuthenticationToken<?> tokenAuthentication
                ? tokenAuthentication.getToken().getExpiresAt()
                : null;
        return new TaskRealtimeIdentity(result, expiresAt);
    }

    @Override
    public int getOrder() {
        return order;
    }

}
