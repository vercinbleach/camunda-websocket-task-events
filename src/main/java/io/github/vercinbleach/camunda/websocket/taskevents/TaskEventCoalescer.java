package io.github.vercinbleach.camunda.websocket.taskevents;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.concurrent.Executor;

@Component
public class TaskEventCoalescer implements AutoCloseable {
    private static final Logger logger = LoggerFactory.getLogger(TaskEventCoalescer.class);

    private final Runnable publisher;
    private final Executor executor;
    private final TaskRealtimeMetrics metrics;
    private final Object stateMonitor = new Object();

    private State state = State.IDLE;

    @Autowired
    public TaskEventCoalescer(
            TaskEventBroadcaster broadcaster,
            @Qualifier("taskRealtimePublisherExecutor") Executor executor,
            TaskRealtimeMetrics metrics) {
        this(() -> broadcaster.publish(), executor, metrics);
    }

    TaskEventCoalescer(Runnable publisher, Executor executor, TaskRealtimeMetrics metrics) {
        this.publisher = publisher;
        this.executor = executor;
        this.metrics = metrics;
    }

    public void request() {
        boolean shouldSchedule;
        synchronized (stateMonitor) {
            if (state == State.CLOSED) {
                return;
            }

            shouldSchedule = state == State.IDLE;
            if (shouldSchedule) {
                state = State.SCHEDULED;
            } else if (state == State.RUNNING) {
                state = State.RUNNING_WITH_TRAILING;
            }
        }

        if (!shouldSchedule) {
            return;
        }

        try {
            executor.execute(this::drain);
        } catch (RuntimeException exception) {
            synchronized (stateMonitor) {
                // Drop the coalesced request on rejection and make the state retryable.
                if (state == State.SCHEDULED) {
                    state = State.IDLE;
                }
            }
            metrics.recordPublisherRejection();
            logger.warn("Realtime publisher executor rejected work");
        }
    }

    @Override
    public void close() {
        synchronized (stateMonitor) {
            state = State.CLOSED;
        }
    }

    private void drain() {
        while (true) {
            synchronized (stateMonitor) {
                if (state == State.CLOSED) {
                    return;
                }
                if (state == State.SCHEDULED || state == State.RUNNING_WITH_TRAILING) {
                    state = State.RUNNING;
                }
            }

            try {
                publisher.run();
            } catch (RuntimeException exception) {
                logger.warn("Realtime publisher failed");
            }

            synchronized (stateMonitor) {
                if (state == State.CLOSED) {
                    return;
                }
                if (state == State.RUNNING_WITH_TRAILING) {
                    state = State.RUNNING;
                    continue;
                }
                state = State.IDLE;
                return;
            }
        }
    }

    enum State {
        IDLE,
        SCHEDULED,
        RUNNING,
        RUNNING_WITH_TRAILING,
        CLOSED
    }
}
