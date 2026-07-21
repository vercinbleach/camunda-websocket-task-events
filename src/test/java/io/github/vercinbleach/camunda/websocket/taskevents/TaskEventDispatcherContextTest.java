package io.github.vercinbleach.camunda.websocket.taskevents;

import org.junit.jupiter.api.Test;
import org.camunda.bpm.engine.IdentityService;
import org.camunda.bpm.engine.ProcessEngine;
import org.camunda.bpm.engine.ProcessEngineConfiguration;
import org.camunda.bpm.engine.TaskService;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TaskEventDispatcherContextTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(RealtimeConstructorConfiguration.class);
    private final WebApplicationContextRunner fullContextRunner = new WebApplicationContextRunner()
            .withUserConfiguration(FullRealtimeConfiguration.class)
            .withPropertyValues("camunda.websocket.task-events.websocket.allowed-origins=http://localhost:3000");

    @Test
    void selectsProductionConstructorsInASpringContext() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(TaskEventDispatcher.class);
            assertThat(context).hasSingleBean(TaskEventBroadcaster.class);
        });
    }

    @Test
    void createsTheCompleteWebSocketBrokerWithoutCircularDependencies() {
        fullContextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(TaskRealtimeWebSocketConfig.class);
            assertThat(context).hasSingleBean(TaskEventBroadcaster.class);
            assertThat(context).hasSingleBean(TaskRealtimeHandshakeHandler.class);
            assertThat(context).hasSingleBean(TaskRealtimeProtocolInterceptor.class);
        });
    }

    @Test
    void createsAnonymousCapableModeWithoutSecurityConfiguration() {
        new WebApplicationContextRunner()
                .withUserConfiguration(FullRealtimeConfiguration.class)
                .withPropertyValues("camunda.websocket.task-events.websocket.allowed-origins=http://localhost:3000")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(TaskRealtimeHandshakeHandler.class);
                });
    }

    @Test
    void closesTheSpringManagedPublisherExecutorWithTheContext() {
        ThreadPoolTaskExecutor[] executor = new ThreadPoolTaskExecutor[1];

        contextRunner.run(context -> executor[0] = context.getBean(ThreadPoolTaskExecutor.class));

        assertThat(executor[0].getCorePoolSize()).isEqualTo(1);
        assertThat(executor[0].getMaxPoolSize()).isEqualTo(1);
        assertThat(executor[0].getThreadPoolExecutor().getQueue().remainingCapacity())
                .isEqualTo(TaskRealtimePublisherConfiguration.PUBLISHER_QUEUE_CAPACITY);
        assertThat(executor[0].getThreadPoolExecutor().isShutdown()).isTrue();
    }

    @Configuration(proxyBeanMethods = false)
    @Import({
            TaskEventBroadcaster.class,
            TaskEventDispatcher.class,
            TaskRealtimePublisherConfiguration.class
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
        IdentityService identityService() {
            return mock(IdentityService.class);
        }

        @Bean
        TaskService taskService() {
            return mock(TaskService.class);
        }

        @Bean
        ProcessEngine processEngine() {
            ProcessEngine engine = mock(ProcessEngine.class);
            ProcessEngineConfiguration configuration = mock(ProcessEngineConfiguration.class);
            when(engine.getProcessEngineConfiguration()).thenReturn(configuration);
            return engine;
        }

    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(RealtimeProperties.class)
    @Import({
            TaskRealtimeWebSocketConfig.class,
            TaskEventBroadcaster.class,
            TaskEventDispatcher.class,
            TaskRealtimePublisherConfiguration.class,
            TaskEventPublicationService.class,
            TaskRealtimeMetrics.class,
            TaskSessionRegistry.class,
            TaskSessionLimitsInterceptor.class,
            TaskWebSocketSessionTracker.class,
            TaskRealtimeHandshakeHandler.class,
            TaskRealtimeProtocolInterceptor.class
    })
    static class FullRealtimeConfiguration {
        @Bean
        MeterRegistry meterRegistry() {
            return new SimpleMeterRegistry();
        }

        @Bean
        IdentityService identityService() {
            return mock(IdentityService.class);
        }

        @Bean
        TaskService taskService() {
            return mock(TaskService.class);
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
