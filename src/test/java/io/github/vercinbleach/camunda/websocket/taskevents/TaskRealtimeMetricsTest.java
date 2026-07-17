package io.github.vercinbleach.camunda.websocket.taskevents;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TaskRealtimeMetricsTest {
    @Test
    void registersObservableCountersWithNonSensitiveNames() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        TaskRealtimeMetrics metrics = new TaskRealtimeMetrics(registry);

        metrics.recordCommittedEvent();
        metrics.recordRejectedAuthentication();
        metrics.recordRejectedSubscription();
        metrics.recordEnvelopeEmitted();
        metrics.recordDeliveryFailure();
        metrics.recordPublisherRejection();

        assertThat(registry.get("task_realtime_committed_events").counter().count()).isEqualTo(1);
        assertThat(registry.get("task_realtime_rejected_authentications").counter().count()).isEqualTo(1);
        assertThat(registry.get("task_realtime_rejected_subscriptions").counter().count()).isEqualTo(1);
        assertThat(registry.get("task_realtime_emitted_envelopes").counter().count()).isEqualTo(1);
        assertThat(registry.get("task_realtime_delivery_failures").counter().count()).isEqualTo(1);
        assertThat(registry.get("task_realtime_publisher_rejections").counter().count()).isEqualTo(1);
    }
}
