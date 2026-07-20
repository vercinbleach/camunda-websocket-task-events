package io.github.vercinbleach.camunda.websocket.taskevents;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class TaskEventDispatcherTest {
    @Test
    void resolvesVisibilityInsideTheDispatcherWorker() {
        QueuedExecutor executor = new QueuedExecutor();
        TaskEventBroadcaster broadcaster = mock(TaskEventBroadcaster.class);
        TaskRealtimePublication pending = TaskRealtimePublication.withoutCapturedRecipients(
                envelope(TaskLifecycleEvent.UPDATE));
        TaskRealtimePublication resolved = new TaskRealtimePublication(
                pending.envelope(), java.util.Set.of("demo"));
        when(broadcaster.finalizePublicationAfterCommit(pending)).thenReturn(resolved);
        TaskEventDispatcher dispatcher = new TaskEventDispatcher(broadcaster, executor, metrics());

        dispatcher.dispatch(pending);
        verifyNoInteractions(broadcaster);
        executor.runAll();

        verify(broadcaster).finalizePublicationAfterCommit(pending);
        verify(broadcaster).publish(resolved);
    }

    @Test
    void preservesEveryTaskEventAndItsOrder() {
        QueuedExecutor executor = new QueuedExecutor();
        List<TaskRealtimePublication> emitted = new ArrayList<>();
        TaskEventDispatcher dispatcher = new TaskEventDispatcher(emitted::add, executor, metrics());
        TaskRealtimeEnvelope create = envelope(TaskLifecycleEvent.CREATE);
        TaskRealtimeEnvelope assignment = envelope(TaskLifecycleEvent.ASSIGNMENT);
        TaskRealtimeEnvelope complete = envelope(TaskLifecycleEvent.COMPLETE);

        dispatcher.dispatch(TaskRealtimePublication.withoutCapturedRecipients(create));
        dispatcher.dispatch(TaskRealtimePublication.withoutCapturedRecipients(assignment));
        dispatcher.dispatch(TaskRealtimePublication.withoutCapturedRecipients(complete));
        executor.runAll();

        assertThat(emitted).extracting(TaskRealtimePublication::envelope)
                .containsExactly(create, assignment, complete);
    }

    @Test
    void recordsRejectedEventsWithoutAffectingTheCaller() {
        Executor executor = command -> {
            throw new RejectedExecutionException("test rejection");
        };
        TaskRealtimeMetrics metrics = metrics();
        TaskEventDispatcher dispatcher = new TaskEventDispatcher(ignored -> {
        }, executor, metrics);

        dispatcher.dispatch(TaskRealtimePublication.withoutCapturedRecipients(
                envelope(TaskLifecycleEvent.UPDATE)));

        assertThat(metrics.getPublisherRejections()).isEqualTo(1);
    }

    @Test
    void isolatesPublisherFailuresFromSubsequentEvents() {
        QueuedExecutor executor = new QueuedExecutor();
        List<TaskRealtimeEnvelope> emitted = new ArrayList<>();
        TaskEventDispatcher dispatcher = new TaskEventDispatcher(event -> {
            if (event.envelope().eventType() == TaskLifecycleEvent.CREATE) {
                throw new IllegalStateException("test failure");
            }
            emitted.add(event.envelope());
        }, executor, metrics());

        dispatcher.dispatch(TaskRealtimePublication.withoutCapturedRecipients(
                envelope(TaskLifecycleEvent.CREATE)));
        dispatcher.dispatch(TaskRealtimePublication.withoutCapturedRecipients(
                envelope(TaskLifecycleEvent.ASSIGNMENT)));
        executor.runAll();

        assertThat(emitted).extracting(TaskRealtimeEnvelope::eventType)
                .containsExactly(TaskLifecycleEvent.ASSIGNMENT);
    }

    private static TaskRealtimeEnvelope envelope(TaskLifecycleEvent eventType) {
        return new TaskRealtimeEnvelope(
                2,
                TaskEventType.TASK_EVENT,
                "task-123",
                eventType,
                eventType == TaskLifecycleEvent.ASSIGNMENT ? "demo" : null);
    }

    private static TaskRealtimeMetrics metrics() {
        return new TaskRealtimeMetrics(new SimpleMeterRegistry());
    }

    private static final class QueuedExecutor implements Executor {
        private final Deque<Runnable> tasks = new ArrayDeque<>();

        @Override
        public void execute(Runnable command) {
            tasks.addLast(command);
        }

        private void runAll() {
            while (!tasks.isEmpty()) {
                tasks.removeFirst().run();
            }
        }
    }
}
