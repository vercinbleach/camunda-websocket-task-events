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

class TaskEventCoalescerTest {
    @Test
    void coalescesARequestBurstIntoOnePublication() {
        QueuedExecutor executor = new QueuedExecutor();
        List<Integer> emitted = new ArrayList<>();
        TaskEventCoalescer coalescer = newCoalescer(executor, emitted);

        for (int i = 0; i < 100; i++) {
            coalescer.request();
        }

        assertThat(executor.size()).isEqualTo(1);
        executor.runAll();

        assertThat(emitted).containsExactly(1);
    }

    @Test
    void publishesExactlyOneTrailingInvalidationWhenRequestArrivesDuringPublish() {
        QueuedExecutor executor = new QueuedExecutor();
        List<Integer> emitted = new ArrayList<>();
        TaskEventCoalescer[] holder = new TaskEventCoalescer[1];
        holder[0] = new TaskEventCoalescer(() -> {
            emitted.add(emitted.size() + 1);
            if (emitted.size() == 1) {
                holder[0].request();
                holder[0].request();
            }
        }, executor, metrics());

        holder[0].request();
        executor.runAll();

        assertThat(emitted).containsExactly(1, 2);
        assertThat(executor.size()).isZero();
    }

    @Test
    void resetsSchedulingStateAfterRejectionSoTheNextRequestCanBeAccepted() {
        QueuedExecutor executor = new QueuedExecutor();
        executor.rejectNext = true;
        List<Integer> emitted = new ArrayList<>();
        TaskRealtimeMetrics metrics = metrics();
        TaskEventCoalescer coalescer = new TaskEventCoalescer(
                () -> emitted.add(1), executor, metrics);

        coalescer.request();
        assertThat(metrics.getPublisherRejections()).isEqualTo(1);
        assertThat(executor.size()).isZero();

        coalescer.request();
        executor.runAll();

        assertThat(emitted).containsExactly(1);
        assertThat(metrics.getPublisherRejections()).isEqualTo(1);
    }

    @Test
    void keepsOneActivePublicationAndDoesNotQueueAnotherWorkerJob() {
        QueuedExecutor executor = new QueuedExecutor();
        List<Integer> emitted = new ArrayList<>();
        TaskEventCoalescer coalescer = new TaskEventCoalescer(
                () -> emitted.add(1), executor, metrics());

        coalescer.request();
        coalescer.request();

        assertThat(executor.size()).isEqualTo(1);
        executor.runAll();

        assertThat(emitted).hasSize(1);
        assertThat(executor.maxSize).isEqualTo(1);
    }

    @Test
    void ignoresQueuedAndFutureRequestsAfterShutdown() {
        QueuedExecutor executor = new QueuedExecutor();
        List<Integer> emitted = new ArrayList<>();
        TaskEventCoalescer coalescer = newCoalescer(executor, emitted);

        coalescer.request();
        coalescer.close();
        coalescer.request();
        executor.runAll();

        assertThat(emitted).isEmpty();
        assertThat(executor.size()).isZero();
    }

    private static TaskEventCoalescer newCoalescer(Executor executor, List<Integer> emitted) {
        return new TaskEventCoalescer(() -> emitted.add(1), executor, metrics());
    }

    private static TaskRealtimeMetrics metrics() {
        return new TaskRealtimeMetrics(new SimpleMeterRegistry());
    }

    private static final class QueuedExecutor implements Executor {
        private final Deque<Runnable> tasks = new ArrayDeque<>();
        private boolean rejectNext;
        private int maxSize;

        @Override
        public void execute(Runnable command) {
            if (rejectNext) {
                rejectNext = false;
                throw new RejectedExecutionException("test rejection");
            }
            tasks.addLast(command);
            maxSize = Math.max(maxSize, tasks.size());
        }

        private int size() {
            return tasks.size();
        }

        private void runAll() {
            while (!tasks.isEmpty()) {
                tasks.removeFirst().run();
            }
        }
    }
}
