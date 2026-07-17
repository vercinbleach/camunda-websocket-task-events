package io.github.vercinbleach.camunda.websocket.taskevents;
import org.camunda.bpm.spring.boot.starter.event.TaskEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Configuration(proxyBeanMethods = false)
public class CamundaTaskEventListener {
    private static final Logger logger = LoggerFactory.getLogger(CamundaTaskEventListener.class);

    private final TaskEventPublicationService publicationService;

    public CamundaTaskEventListener(TaskEventPublicationService publicationService) {
        this.publicationService = publicationService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onTaskEvent(TaskEvent ignoredTaskEvent) {
        try {
            publicationService.enqueueTaskInvalidation();
        } catch (RuntimeException exception) {
            // Realtime is best-effort and must never change the Camunda command result.
            logger.warn("Could not enqueue realtime publication without task or token details");
        }
    }
}
