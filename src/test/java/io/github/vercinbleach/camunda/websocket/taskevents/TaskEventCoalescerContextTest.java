package io.github.vercinbleach.camunda.websocket.taskevents;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.security.oauth2.jwt.JwtDecoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class TaskEventCoalescerContextTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(RealtimeConstructorConfiguration.class)
            .withPropertyValues("camunda.websocket.task-events.authentication.provider=stomp-bearer-jwt");
    private final WebApplicationContextRunner fullContextRunner = new WebApplicationContextRunner()
            .withUserConfiguration(FullRealtimeConfiguration.class, JwtDecoderConfiguration.class)
            .withPropertyValues(
                    "camunda.websocket.task-events.websocket.allowed-origins=http://localhost:3000",
                    "camunda.websocket.task-events.authentication.provider=stomp-bearer-jwt",
                    "camunda.websocket.task-events.authentication.jwt.issuer=https://id.example.com");

    @Test
    void selectsProductionConstructorsInASpringContext() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(TaskEventCoalescer.class);
            assertThat(context).hasSingleBean(TaskEventBroadcaster.class);
            assertThat(context).hasSingleBean(TaskRealtimeAuthenticationInterceptor.class);
            assertThat(context).hasSingleBean(StompBearerJwtAuthenticationProvider.class);
        });
    }

    @Test
    void createsTheCompleteWebSocketBrokerWithoutCircularDependencies() {
        fullContextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(TaskRealtimeWebSocketConfig.class);
            assertThat(context).hasSingleBean(TaskEventBroadcaster.class);
            assertThat(context).hasSingleBean(TaskRealtimeAuthenticationInterceptor.class);
            assertThat(context).hasSingleBean(StompBearerJwtAuthenticationProvider.class);
        });
    }

    @Test
    void createsHttpPrincipalModeWithoutAJwtDecoder() {
        new WebApplicationContextRunner()
                .withUserConfiguration(FullRealtimeConfiguration.class)
                .withPropertyValues(
                        "camunda.websocket.task-events.websocket.allowed-origins=http://localhost:3000",
                        "camunda.websocket.task-events.authentication.provider=http-principal")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(HttpPrincipalAuthenticationProvider.class);
                    assertThat(context).doesNotHaveBean(StompBearerJwtAuthenticationProvider.class);
                    assertThat(context).doesNotHaveBean(JwtDecoder.class);
                });
    }

    @Test
    void closesTheSpringManagedPublisherExecutorWithTheContext() {
        ThreadPoolTaskExecutor[] executor = new ThreadPoolTaskExecutor[1];

        contextRunner.run(context -> executor[0] = context.getBean(ThreadPoolTaskExecutor.class));

        assertThat(executor[0].getCorePoolSize()).isEqualTo(1);
        assertThat(executor[0].getMaxPoolSize()).isEqualTo(1);
        assertThat(executor[0].getThreadPoolExecutor().getQueue().remainingCapacity()).isZero();
        assertThat(executor[0].getThreadPoolExecutor().isShutdown()).isTrue();
    }

    @Configuration(proxyBeanMethods = false)
    @Import({
            TaskEventBroadcaster.class,
            TaskEventCoalescer.class,
            TaskRealtimePublisherConfiguration.class,
            TaskRealtimeAuthenticationInterceptor.class,
            StompBearerJwtAuthenticationProvider.class,
            HttpPrincipalAuthenticationProvider.class
    })
    static class RealtimeConstructorConfiguration {
        @Bean
        SimpMessagingTemplate simpMessagingTemplate() {
            return mock(SimpMessagingTemplate.class);
        }

        @Bean
        SimpUserRegistry simpUserRegistry() {
            return mock(SimpUserRegistry.class);
        }

        @Bean
        TaskRealtimeMetrics taskRealtimeMetrics() {
            return mock(TaskRealtimeMetrics.class);
        }

        @Bean
        TaskSessionRegistry taskSessionRegistry() {
            return mock(TaskSessionRegistry.class);
        }

        @Bean
        JwtDecoder jwtDecoder() {
            return mock(JwtDecoder.class);
        }

        @Bean
        RealtimeProperties realtimeProperties() {
            RealtimeProperties properties = new RealtimeProperties();
            properties.getAuthentication().setProvider(StompBearerJwtAuthenticationProvider.ID);
            return properties;
        }

    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(RealtimeProperties.class)
    @Import({
            TaskRealtimeWebSocketConfig.class,
            TaskRealtimeChannelSecurityConfig.class,
            TaskEventBroadcaster.class,
            TaskEventCoalescer.class,
            TaskRealtimePublisherConfiguration.class,
            TaskEventPublicationService.class,
            TaskRealtimeMetrics.class,
            TaskSessionRegistry.class,
            TaskSessionLimitsInterceptor.class,
            TaskWebSocketSessionTracker.class,
            TaskRealtimeAuthenticationInterceptor.class,
            StompBearerJwtAuthenticationProvider.class,
            HttpPrincipalAuthenticationProvider.class,
            TaskStompAuthorizationManager.class
    })
    static class FullRealtimeConfiguration {
        @Bean
        MeterRegistry meterRegistry() {
            return new SimpleMeterRegistry();
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class JwtDecoderConfiguration {
        @Bean
        JwtDecoder jwtDecoder() {
            return mock(JwtDecoder.class);
        }
    }
}
