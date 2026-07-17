package io.github.vercinbleach.camunda.websocket.taskevents;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CamundaTaskEventsEnvironmentPostProcessorTest {
    private final CamundaTaskEventsEnvironmentPostProcessor processor =
            new CamundaTaskEventsEnvironmentPostProcessor();

    @Test
    void suppliesNonSkippableEventingAsALowPrecedenceDefault() {
        StandardEnvironment environment = new StandardEnvironment();

        processor.postProcessEnvironment(environment, new SpringApplication());

        assertThat(environment.getProperty(
                CamundaTaskEventsEnvironmentPostProcessor.PROPERTY,
                Boolean.class)).isFalse();
    }

    @Test
    void doesNotOverrideAnExplicitApplicationValue() {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource(
                "application",
                Map.of(CamundaTaskEventsEnvironmentPostProcessor.PROPERTY, true)));

        processor.postProcessEnvironment(environment, new SpringApplication());

        assertThat(environment.getProperty(
                CamundaTaskEventsEnvironmentPostProcessor.PROPERTY,
                Boolean.class)).isTrue();
    }
}
