package io.github.vercinbleach.camunda.websocket.taskevents;

import org.camunda.bpm.engine.ProcessEngine;
import org.camunda.bpm.engine.rest.security.auth.AuthenticationProvider;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@ConditionalOnClass(AuthenticationProvider.class)
public class CamundaRestAuthenticationTaskEventsAutoConfiguration {
    @Bean
    TaskRealtimeHandshakeAuthenticator camundaRestTaskRealtimeHandshakeAuthenticator(
            ObjectProvider<AuthenticationProvider> providers,
            ObjectProvider<ProcessEngine> processEngines,
            AutowireCapableBeanFactory beanFactory,
            ObjectProvider<TaskRealtimePrincipalFactory> principalFactories) {
        return new CamundaRestAuthenticationProviderAdapter(
                providers, processEngines, beanFactory, principalFactories);
    }
}
