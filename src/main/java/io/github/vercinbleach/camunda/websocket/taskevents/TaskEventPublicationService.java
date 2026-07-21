package io.github.vercinbleach.camunda.websocket.taskevents;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class TaskEventPublicationService {
    private static final Logger logger = LoggerFactory.getLogger(TaskEventPublicationService.class);

    private final TaskEventDispatcher dispatcher;
    private final TaskRealtimeMetrics metrics;

    public TaskEventPublicationService(TaskEventDispatcher dispatcher, TaskRealtimeMetrics metrics) {
        this.dispatcher = dispatcher;
        this.metrics = metrics;
    }

    public void enqueue(TaskRealtimePublication publication) {
        metrics.recordCommittedEvent();
        try {
            dispatcher.dispatch(publication);
        } catch (RuntimeException exception) {
            logger.warn("Realtime task event enqueue failed without payload details");
        }
    }

}
