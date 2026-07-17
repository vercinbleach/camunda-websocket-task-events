package io.github.vercinbleach.camunda.websocket.taskevents;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.util.Map;

public class CamundaTaskEventsEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {
    static final String PROPERTY = "camunda.bpm.eventing.skippable";
    private static final String PROPERTY_SOURCE = "camundaWebSocketTaskEventsDefaults";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        if (!environment.getPropertySources().contains(PROPERTY_SOURCE)) {
            environment.getPropertySources().addLast(new MapPropertySource(
                    PROPERTY_SOURCE,
                    Map.of(PROPERTY, false)));
        }
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}
