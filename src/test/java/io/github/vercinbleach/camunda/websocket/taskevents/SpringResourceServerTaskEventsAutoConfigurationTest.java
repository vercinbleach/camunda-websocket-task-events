package io.github.vercinbleach.camunda.websocket.taskevents;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.boot.autoconfigure.security.oauth2.resource.servlet.OAuth2ResourceServerAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.DefaultOAuth2AuthenticatedPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.introspection.OpaqueTokenIntrospector;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SpringResourceServerTaskEventsAutoConfigurationTest {
    @Test
    void jwtReusesTheApplicationsDecoderAndPrincipalConverter() {
        Instant expiresAt = Instant.now().plusSeconds(300);
        Jwt jwt = Jwt.withTokenValue("valid-jwt")
                .header("alg", "RS256")
                .subject("technical-subject")
                .claim("preferred_username", "demo")
                .issuedAt(Instant.now())
                .expiresAt(expiresAt)
                .build();
        JwtDecoder decoder = mock(JwtDecoder.class);
        when(decoder.decode("valid-jwt")).thenReturn(jwt);
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setPrincipalClaimName("preferred_username");

        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(SpringJwtTaskEventsAutoConfiguration.class))
                .withBean(JwtDecoder.class, () -> decoder)
                .withBean(JwtAuthenticationConverter.class, () -> converter)
                .run(context -> {
                    TaskRealtimeCredentialAuthenticator authenticator =
                            context.getBean(TaskRealtimeCredentialAuthenticator.class);
                    TaskRealtimeIdentity identity = authenticator.authenticate(connect("Bearer valid-jwt"));

                    assertThat(identity.principal().getName()).isEqualTo("demo");
                    assertThat(identity.expiresAt()).isEqualTo(expiresAt);
                });
    }

    @Test
    void opaqueTokenReusesTheApplicationsIntrospector() {
        OpaqueTokenIntrospector introspector = mock(OpaqueTokenIntrospector.class);
        when(introspector.introspect("opaque-token")).thenReturn(new DefaultOAuth2AuthenticatedPrincipal(
                "opaque-user",
                Map.of("sub", "opaque-user"),
                List.of(new SimpleGrantedAuthority("tasks"))));

        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(SpringOpaqueTokenTaskEventsAutoConfiguration.class))
                .withBean(OpaqueTokenIntrospector.class, () -> introspector)
                .run(context -> {
                    TaskRealtimeCredentialAuthenticator authenticator =
                            context.getBean(TaskRealtimeCredentialAuthenticator.class);
                    TaskRealtimeIdentity identity = authenticator.authenticate(connect("Bearer opaque-token"));

                    assertThat(identity.principal().getName()).isEqualTo("opaque-user");
                });
    }

    @Test
    void jwtAdapterRunsAfterBootCreatesTheDecoderFromProperties() {
        new WebApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        OAuth2ResourceServerAutoConfiguration.class,
                        SecurityAutoConfiguration.class,
                        SpringJwtTaskEventsAutoConfiguration.class))
                .withPropertyValues(
                        "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=https://id.example.invalid/jwks")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(JwtDecoder.class);
                    assertThat(context).hasBean("jwtTaskRealtimeAuthenticator");
                });
    }

    private StompHeaderAccessor connect(String authorization) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.addNativeHeader("Authorization", authorization);
        return accessor;
    }
}
