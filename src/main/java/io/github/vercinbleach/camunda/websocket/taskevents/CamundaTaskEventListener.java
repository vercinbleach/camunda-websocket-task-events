package io.github.vercinbleach.camunda.websocket.taskevents;

import org.camunda.bpm.spring.boot.starter.event.TaskEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Configuration(proxyBeanMethods = false)
public class CamundaTaskEventListener {
    private static final Logger logger = LoggerFactory.getLogger(CamundaTaskEventListener.class);

    private final TaskEventPublicationService publicationService;
    private final ApplicationEventPublisher eventPublisher;

    public CamundaTaskEventListener(
            TaskEventPublicationService publicationService,
            ApplicationEventPublisher eventPublisher) {
        this.publicationService = publicationService;
        this.eventPublisher = eventPublisher;
    }

    @EventListener
    public void captureTaskEvent(TaskEvent taskEvent) {
        try {
            TaskRealtimeEnvelope.from(taskEvent)
                    .map(TaskRealtimePublication::withoutCapturedRecipients)
                    .ifPresent(eventPublisher::publishEvent);
        } catch (RuntimeException exception) {
            logger.warn("Could not capture realtime task event without task or token details");
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publishAfterCommit(TaskRealtimePublication publication) {
        try {
            publicationService.enqueue(publication);
        } catch (RuntimeException exception) {
            logger.warn("Could not enqueue realtime publication without task or token details");
        }
    }
}
