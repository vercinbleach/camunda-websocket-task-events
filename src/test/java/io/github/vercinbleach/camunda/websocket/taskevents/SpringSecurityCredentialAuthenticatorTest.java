package io.github.vercinbleach.camunda.websocket.taskevents;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.oauth2.server.resource.authentication.BearerTokenAuthenticationToken;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SpringSecurityCredentialAuthenticatorTest {
    @Test
    void delegatesBearerAuthenticationToTheApplicationsAuthenticationManager() {
        AuthenticationManager manager = mock(AuthenticationManager.class);
        TestingAuthenticationToken authenticated = new TestingAuthenticationToken("demo", null, "tasks");
        authenticated.setAuthenticated(true);
        when(manager.authenticate(any())).thenReturn(authenticated);
        SpringSecurityCredentialAuthenticator authenticator = new SpringSecurityCredentialAuthenticator(
                manager,
                SpringSecurityCredentialAuthenticator.RESOURCE_SERVER_ORDER);
        StompHeaderAccessor connect = connect("Bearer application-token");

        TaskRealtimeIdentity identity = authenticator.authenticate(connect);

        assertThat(identity.principal()).isSameAs(authenticated);
        assertThat(identity.principal().getName()).isEqualTo("demo");
        verify(manager).authenticate(any(BearerTokenAuthenticationToken.class));
    }

    private StompHeaderAccessor connect(String authorization) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.addNativeHeader("Authorization", authorization);
        return accessor;
    }
}
