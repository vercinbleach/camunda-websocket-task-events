package io.github.vercinbleach.camunda.websocket.taskevents;

import org.camunda.bpm.engine.IdentityService;
import org.camunda.bpm.engine.ProcessEngine;
import org.camunda.bpm.engine.ProcessEngineConfiguration;
import org.camunda.bpm.engine.TaskService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CamundaWebSocketTaskEventsAutoConfigurationTest {
    private final WebApplicationContextRunner contextRunner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(CamundaWebSocketTaskEventsAutoConfiguration.class))
            .withUserConfiguration(TestEngineServices.class)
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
                .withUserConfiguration(TestEngineServices.class)
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
                .withUserConfiguration(TestEngineServices.class)
                .withPropertyValues("camunda.bpm.eventing.skippable=false")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(TaskRealtimeWebSocketConfig.class);
                    assertThat(context).doesNotHaveBean(TaskRealtimeHandshakeAuthenticator.class);
                });
    }

    @Configuration(proxyBeanMethods = false)
    static class TestEngineServices {
        @Bean
        TaskService taskService() {
            return mock(TaskService.class);
        }

        @Bean
        IdentityService identityService() {
            return mock(IdentityService.class);
        }

        @Bean
        ProcessEngine processEngine() {
            ProcessEngine engine = mock(ProcessEngine.class);
            ProcessEngineConfiguration configuration = mock(ProcessEngineConfiguration.class);
            when(engine.getProcessEngineConfiguration()).thenReturn(configuration);
            return engine;
        }
    }
}
