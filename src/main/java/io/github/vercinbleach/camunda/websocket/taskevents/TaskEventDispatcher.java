package io.github.vercinbleach.camunda.websocket.taskevents;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

@Component
public class TaskEventDispatcher {
    private static final Logger logger = LoggerFactory.getLogger(TaskEventDispatcher.class);

    private final Consumer<TaskRealtimePublication> publisher;
    private final Executor executor;
    private final TaskRealtimeMetrics metrics;

    @Autowired
    public TaskEventDispatcher(
            TaskEventBroadcaster broadcaster,
            @Qualifier("taskRealtimePublisherExecutor") Executor executor,
            TaskRealtimeMetrics metrics) {
        this(publication -> broadcaster.publish(
                broadcaster.finalizePublicationAfterCommit(publication)), executor, metrics);
    }

    TaskEventDispatcher(
            Consumer<TaskRealtimePublication> publisher,
            Executor executor,
            TaskRealtimeMetrics metrics) {
        this.publisher = publisher;
        this.executor = executor;
        this.metrics = metrics;
    }

    public void dispatch(TaskRealtimePublication publication) {
        Objects.requireNonNull(publication, "publication");
        try {
            executor.execute(() -> publish(publication));
        } catch (RuntimeException exception) {
            metrics.recordPublisherRejection();
            logger.warn("Realtime publisher executor rejected task event");
        }
    }

    private void publish(TaskRealtimePublication publication) {
        try {
            publisher.accept(publication);
        } catch (RuntimeException exception) {
            logger.warn("Realtime task event publisher failed");
        }
    }
}
