package io.github.vercinbleach.camunda.websocket.taskevents;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.messaging.access.intercept.AuthorizationChannelInterceptor;
import org.springframework.security.messaging.context.SecurityContextChannelInterceptor;

@Configuration(proxyBeanMethods = false)
public class TaskRealtimeChannelSecurityConfig {
    @Bean
    public SecurityContextChannelInterceptor securityContextChannelInterceptor() {
        return new SecurityContextChannelInterceptor();
    }

    @Bean
    public AuthorizationChannelInterceptor authorizationChannelInterceptor(
            TaskStompAuthorizationManager authorizationManager) {
        AuthorizationManager<Message<?>> manager = authorizationManager;
        return new AuthorizationChannelInterceptor(manager);
    }
}
