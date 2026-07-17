package io.github.vercinbleach.camunda.websocket.taskevents;

import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HttpPrincipalAuthenticationProviderTest {
    private final HttpPrincipalAuthenticationProvider provider = new HttpPrincipalAuthenticationProvider();

    @Test
    void reusesTheAuthenticatedHandshakePrincipalWithoutExpiry() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setUser(UsernamePasswordAuthenticationToken.authenticated("demo", null, List.of()));

        TaskRealtimeAuthentication result = provider.authenticate(accessor);

        assertThat(result.principal().getName()).isEqualTo("demo");
        assertThat(result.expiresAt()).isNull();
    }

    @Test
    void rejectsAnAnonymousHandshake() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);

        assertThatThrownBy(() -> provider.authenticate(accessor))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
