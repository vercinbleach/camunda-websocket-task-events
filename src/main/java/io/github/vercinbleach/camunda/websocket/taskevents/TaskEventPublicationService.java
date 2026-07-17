package io.github.vercinbleach.camunda.websocket.taskevents;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class TaskEventPublicationService {
    private static final Logger logger = LoggerFactory.getLogger(TaskEventPublicationService.class);

    private final TaskEventCoalescer coalescer;
    private final TaskRealtimeMetrics metrics;

    public TaskEventPublicationService(TaskEventCoalescer coalescer, TaskRealtimeMetrics metrics) {
        this.coalescer = coalescer;
        this.metrics = metrics;
    }

    public void enqueueTaskInvalidation() {
        metrics.recordCommittedEvent();
        try {
            coalescer.request();
        } catch (RuntimeException exception) {
            logger.warn("Realtime invalidation enqueue failed without payload details");
        }
    }

}
