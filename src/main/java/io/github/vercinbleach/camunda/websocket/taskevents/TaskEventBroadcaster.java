package io.github.vercinbleach.camunda.websocket.taskevents;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.user.SimpUser;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
public class TaskEventBroadcaster {
    public static final String USER_QUEUE_DESTINATION = "/queue/task-events";

    private static final Logger logger = LoggerFactory.getLogger(TaskEventBroadcaster.class);

    private final SimpMessagingTemplate messagingTemplate;
    private final SimpUserRegistry userRegistry;
    private final TaskRealtimeMetrics metrics;

    @Autowired
    public TaskEventBroadcaster(SimpMessagingTemplate messagingTemplate,
                                SimpUserRegistry userRegistry,
                                TaskRealtimeMetrics metrics) {
        this.messagingTemplate = messagingTemplate;
        this.userRegistry = userRegistry;
        this.metrics = metrics;
    }

    public void publish() {
        Set<SimpUser> users;
        try {
            users = userRegistry.getUsers();
        } catch (RuntimeException exception) {
            logger.warn("Realtime user registry unavailable without payload details");
            return;
        }
        if (users == null || users.isEmpty()) {
            return;
        }

        TaskRealtimeEnvelope envelope = new TaskRealtimeEnvelope(
                TaskRealtimeEnvelope.CURRENT_SCHEMA_VERSION,
                TaskEventType.TASKS_INVALIDATED);

        int attempted = 0;
        for (SimpUser user : users) {
            if (user == null || user.getName() == null || user.getName().isBlank()) {
                continue;
            }
            try {
                messagingTemplate.convertAndSendToUser(user.getName(), USER_QUEUE_DESTINATION, envelope);
                attempted++;
            } catch (RuntimeException exception) {
                metrics.recordDeliveryFailure();
                logger.debug("Realtime delivery failed without user or payload details");
            }
        }
        if (attempted > 0) {
            metrics.recordEnvelopeEmitted();
        }
        logger.debug("Realtime invalidation attempted recipients={}", attempted);
    }
}
