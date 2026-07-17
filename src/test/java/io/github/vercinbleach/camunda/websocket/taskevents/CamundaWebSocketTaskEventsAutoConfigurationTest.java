package io.github.vercinbleach.camunda.websocket.taskevents;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

import static org.assertj.core.api.Assertions.assertThat;

class CamundaWebSocketTaskEventsAutoConfigurationTest {
    private final WebApplicationContextRunner contextRunner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(CamundaWebSocketTaskEventsAutoConfiguration.class))
            .withPropertyValues("camunda.bpm.eventing.skippable=false");

    @Test
    void configuresTheEndpointWithoutAnAuthenticationProvider() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(TaskRealtimeWebSocketConfig.class);
            assertThat(context).hasSingleBean(TaskRealtimeHandshakeHandler.class);
            assertThat(context).hasSingleBean(TaskRealtimeProtocolInterceptor.class);
            assertThat(context.getBean(RealtimeProperties.class).getWebsocket().getEndpoint())
                    .isEqualTo("/ws/task-events");
        });
    }

    @Test
    void failsClosedWhenCamundaEventingIsExplicitlySkippable() {
        contextRunner
                .withPropertyValues("camunda.bpm.eventing.skippable=true")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void coreStillStartsWhenSpringSecurityIsNotOnTheConsumerClasspath() {
        new WebApplicationContextRunner()
                .withClassLoader(new FilteredClassLoader("org.springframework.security"))
                .withConfiguration(AutoConfigurations.of(
                        CamundaWebSocketTaskEventsAutoConfiguration.class,
                        SpringJwtTaskEventsAutoConfiguration.class,
                        SpringOpaqueTokenTaskEventsAutoConfiguration.class,
                        SpringSecurityPrincipalTaskEventsAutoConfiguration.class))
                .withPropertyValues("camunda.bpm.eventing.skippable=false")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(TaskRealtimeWebSocketConfig.class);
                    assertThat(context).doesNotHaveBean(TaskRealtimeCredentialAuthenticator.class);
                });
    }

    @Test
    void authenticationConfigurerRunsBeforeSpringWebSocketSecurity() {
        Order order = TaskRealtimeWebSocketConfig.class.getAnnotation(Order.class);

        assertThat(order).isNotNull();
        assertThat(order.value()).isEqualTo(Ordered.HIGHEST_PRECEDENCE + 99);
    }

    @Test
    void coreStillStartsWhenCamundaRestIsNotOnTheConsumerClasspath() {
        new WebApplicationContextRunner()
                .withClassLoader(new FilteredClassLoader("org.camunda.bpm.engine.rest"))
                .withConfiguration(AutoConfigurations.of(
                        CamundaWebSocketTaskEventsAutoConfiguration.class,
                        CamundaRestAuthenticationTaskEventsAutoConfiguration.class))
                .withPropertyValues("camunda.bpm.eventing.skippable=false")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(TaskRealtimeWebSocketConfig.class);
                    assertThat(context).doesNotHaveBean(TaskRealtimeHandshakeAuthenticator.class);
                });
    }
}
