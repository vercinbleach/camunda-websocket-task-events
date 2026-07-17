package io.github.vercinbleach.camunda.websocket.taskevents;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StompBearerJwtAuthenticationProviderTest {
    private static final Instant NOW = Instant.parse("2026-07-14T10:00:00Z");

    @Mock
    private JwtDecoder jwtDecoder;

    private RealtimeProperties properties;
    private StompBearerJwtAuthenticationProvider provider;

    @BeforeEach
    void setUp() {
        properties = new RealtimeProperties();
        properties.getAuthentication().setProvider(StompBearerJwtAuthenticationProvider.ID);
        properties.getAuthentication().getJwt().setIssuer("https://issuer.example/realms/camunda");
        properties.getAuthentication().getJwt().setAudience("camunda-engine");
        properties.getAuthentication().getJwt().setAuthorizedParty("camunda-frontend");
        provider = new StompBearerJwtAuthenticationProvider(
                jwtDecoder,
                properties,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void authenticatesBearerAndReturnsItsExpiry() {
        Jwt validJwt = jwt(List.of("camunda-engine"), "camunda-frontend", NOW.plusSeconds(60));
        when(jwtDecoder.decode("signed-token")).thenReturn(validJwt);

        TaskRealtimeAuthentication result = provider.authenticate(connect("Bearer signed-token"));

        assertThat(result.principal().getName()).isEqualTo("demo");
        assertThat(result.expiresAt()).isEqualTo(NOW.plusSeconds(60));
    }

    @Test
    void supportsAConfiguredPrincipalClaim() {
        properties.getAuthentication().getJwt().setPrincipalClaim("sub");
        Jwt validJwt = Jwt.withTokenValue("signed-token")
                .header("alg", "RS256")
                .issuer(properties.getAuthentication().getJwt().getIssuer())
                .audience(List.of("camunda-engine"))
                .claim("azp", "camunda-frontend")
                .subject("user-123")
                .issuedAt(NOW.minusSeconds(10))
                .expiresAt(NOW.plusSeconds(60))
                .build();
        when(jwtDecoder.decode("signed-token")).thenReturn(validJwt);

        assertThat(provider.authenticate(connect("Bearer signed-token")).principal().getName())
                .isEqualTo("user-123");
    }

    @Test
    void rejectsDuplicateAuthorizationAndInvalidClaims() {
        StompHeaderAccessor duplicate = connect("Bearer one");
        duplicate.addNativeHeader("Authorization", "Bearer two");
        assertThatThrownBy(() -> provider.authenticate(duplicate))
                .isInstanceOf(IllegalArgumentException.class);

        when(jwtDecoder.decode("signed-token"))
                .thenReturn(jwt(List.of("wrong-audience"), "wrong-client", NOW.minusSeconds(1)));
        assertThatThrownBy(() -> provider.authenticate(connect("Bearer signed-token")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private StompHeaderAccessor connect(String authorization) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setSessionId("session-1");
        accessor.addNativeHeader("Authorization", authorization);
        return accessor;
    }

    private Jwt jwt(List<String> audience, String azp, Instant expiresAt) {
        return Jwt.withTokenValue("signed-token")
                .header("alg", "RS256")
                .issuer(properties.getAuthentication().getJwt().getIssuer())
                .audience(audience)
                .claim("azp", azp)
                .claim("preferred_username", "demo")
                .issuedAt(NOW.minusSeconds(10))
                .expiresAt(expiresAt)
                .build();
    }
}
