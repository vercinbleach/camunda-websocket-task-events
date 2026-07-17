package io.github.vercinbleach.camunda.websocket.taskevents;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import java.security.Principal;
import java.util.List;

final class SpringSecurityTaskRealtimePrincipalFactory implements TaskRealtimePrincipalFactory {
    @Override
    public Principal authenticated(String username) {
        return UsernamePasswordAuthenticationToken.authenticated(username, null, List.of());
    }
}
