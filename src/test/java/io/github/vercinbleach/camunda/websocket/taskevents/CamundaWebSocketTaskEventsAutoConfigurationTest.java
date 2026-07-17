package io.github.vercinbleach.camunda.websocket.taskevents;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class CamundaWebSocketTaskEventsAutoConfigurationTest {
    private final WebApplicationContextRunner contextRunner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(CamundaWebSocketTaskEventsAutoConfiguration.class))
            .withPropertyValues("camunda.bpm.eventing.skippable=false");

    @Test
    void configuresTheEndpointWithTheDefaultHttpPrincipalProvider() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(TaskRealtimeWebSocketConfig.class);
            assertThat(context).hasSingleBean(TaskRealtimeAuthenticationInterceptor.class);
            assertThat(context).hasSingleBean(HttpPrincipalAuthenticationProvider.class);
            assertThat(context).doesNotHaveBean(StompBearerJwtAuthenticationProvider.class);
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
}
