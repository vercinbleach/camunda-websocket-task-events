package io.github.vercinbleach.camunda.websocket.taskevents;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StompBearerJwtAuthenticationProviderTest {
    private static final Instant NOW = Instant.parse("2026-07-14T10:00:00Z");

    @Mock
    private JwtDecoder jwtDecoder;

    @Test
    void reusesTheApplicationDecoderAndAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setPrincipalClaimName("preferred_username");
        StompBearerJwtAuthenticationProvider provider =
                new StompBearerJwtAuthenticationProvider(jwtDecoder, converter);
        Jwt validJwt = jwt(NOW.plusSeconds(60));
        when(jwtDecoder.decode("signed-token")).thenReturn(validJwt);

        TaskRealtimeAuthentication result = provider.authenticate(connect("Bearer signed-token"));

        assertThat(result.principal().getName()).isEqualTo("demo");
        assertThat(result.expiresAt()).isEqualTo(NOW.plusSeconds(60));
    }

    @Test
    void delegatesClaimValidationToTheApplicationDecoder() {
        StompBearerJwtAuthenticationProvider provider = new StompBearerJwtAuthenticationProvider(
                jwtDecoder,
                new JwtAuthenticationConverter());
        when(jwtDecoder.decode("invalid-token")).thenThrow(new JwtException("application validation rejected token"));

        assertThatThrownBy(() -> provider.authenticate(connect("Bearer invalid-token")))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void rejectsDuplicateAuthorizationAndMissingExpiration() {
        StompBearerJwtAuthenticationProvider provider = new StompBearerJwtAuthenticationProvider(
                jwtDecoder,
                new JwtAuthenticationConverter());
        StompHeaderAccessor duplicate = connect("Bearer one");
        duplicate.addNativeHeader("Authorization", "Bearer two");
        assertThatThrownBy(() -> provider.authenticate(duplicate))
                .isInstanceOf(IllegalArgumentException.class);

        Jwt withoutExpiration = Jwt.withTokenValue("signed-token")
                .header("alg", "RS256")
                .subject("user-123")
                .issuedAt(NOW.minusSeconds(10))
                .build();
        when(jwtDecoder.decode("signed-token")).thenReturn(withoutExpiration);
        assertThatThrownBy(() -> provider.authenticate(connect("Bearer signed-token")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("expiration");
    }

    private StompHeaderAccessor connect(String authorization) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setSessionId("session-1");
        accessor.addNativeHeader("Authorization", authorization);
        return accessor;
    }

    private Jwt jwt(Instant expiresAt) {
        return Jwt.withTokenValue("signed-token")
                .header("alg", "RS256")
                .issuer("https://id.example.com/realms/workflows")
                .audience(java.util.List.of("camunda-engine"))
                .claim("azp", "workflow-frontend")
                .claim("preferred_username", "demo")
                .subject("user-123")
                .issuedAt(NOW.minusSeconds(10))
                .expiresAt(expiresAt)
                .build();
    }
}
