package io.github.vercinbleach.camunda.websocket.taskevents;

import jakarta.annotation.PostConstruct;
import org.springframework.core.env.Environment;

public class CamundaEventingConfigurationValidator {
    private final Environment environment;

    public CamundaEventingConfigurationValidator(Environment environment) {
        this.environment = environment;
    }

    @PostConstruct
    void validate() {
        if (environment.getProperty(
                CamundaTaskEventsEnvironmentPostProcessor.PROPERTY,
                Boolean.class,
                true)) {
            throw new IllegalStateException(
                    "camunda.bpm.eventing.skippable must be false so task invalidations cannot be skipped");
        }
    }
}
