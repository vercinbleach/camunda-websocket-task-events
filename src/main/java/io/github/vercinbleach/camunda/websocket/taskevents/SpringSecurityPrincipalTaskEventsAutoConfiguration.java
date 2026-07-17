package io.github.vercinbleach.camunda.websocket.taskevents;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.security.core.Authentication;

@AutoConfiguration
@ConditionalOnClass(Authentication.class)
public class SpringSecurityPrincipalTaskEventsAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean(TaskRealtimePrincipalFactory.class)
    TaskRealtimePrincipalFactory springSecurityTaskRealtimePrincipalFactory() {
        return new SpringSecurityTaskRealtimePrincipalFactory();
    }
}
