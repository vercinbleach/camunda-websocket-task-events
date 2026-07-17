package io.github.vercinbleach.camunda.websocket.taskevents;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.security.core.Authentication;

import static org.assertj.core.api.Assertions.assertThat;

class SpringSecurityPrincipalTaskEventsAutoConfigurationTest {
    @Test
    void createsAnAuthenticatedPrincipalForCamundaProviders() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        SpringSecurityPrincipalTaskEventsAutoConfiguration.class))
                .run(context -> {
                    TaskRealtimePrincipalFactory factory = context.getBean(TaskRealtimePrincipalFactory.class);
                    var principal = factory.authenticated("demo");

                    assertThat(principal).isInstanceOf(Authentication.class);
                    assertThat(((Authentication) principal).isAuthenticated()).isTrue();
                    assertThat(principal.getName()).isEqualTo("demo");
                });
    }
}
