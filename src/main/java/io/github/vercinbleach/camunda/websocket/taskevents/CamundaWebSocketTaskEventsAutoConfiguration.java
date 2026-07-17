package io.github.vercinbleach.camunda.websocket.taskevents;

import org.camunda.bpm.engine.ProcessEngine;
import org.camunda.bpm.spring.boot.starter.event.TaskEvent;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Import;
import org.springframework.messaging.simp.SimpMessagingTemplate;

@AutoConfiguration
@ConditionalOnClass({ProcessEngine.class, TaskEvent.class, SimpMessagingTemplate.class})
@EnableConfigurationProperties(RealtimeProperties.class)
@Import({
        CamundaEventingConfigurationValidator.class,
        CamundaTaskEventListener.class,
        HttpPrincipalAuthenticationProvider.class,
        StompBearerJwtAuthenticationProvider.class,
        TaskEventBroadcaster.class,
        TaskEventCoalescer.class,
        TaskEventPublicationService.class,
        TaskRealtimeAuthenticationInterceptor.class,
        TaskRealtimeChannelSecurityConfig.class,
        TaskRealtimeMetrics.class,
        TaskRealtimePublisherConfiguration.class,
        TaskRealtimeWebSocketConfig.class,
        TaskSessionLimitsInterceptor.class,
        TaskSessionRegistry.class,
        TaskStompAuthorizationManager.class,
        TaskWebSocketSessionTracker.class
})
public class CamundaWebSocketTaskEventsAutoConfiguration {
}
