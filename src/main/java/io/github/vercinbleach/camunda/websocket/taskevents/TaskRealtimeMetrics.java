package io.github.vercinbleach.camunda.websocket.taskevents;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

@Component
public class TaskRealtimeMetrics {
    private final Counter committedEvents;
    private final Counter rejectedSubscriptions;
    private final Counter emittedEnvelopes;
    private final Counter deliveryFailures;
    private final Counter publisherRejections;
    private final Counter rejectedAuthentications;

    public TaskRealtimeMetrics() {
        this(new SimpleMeterRegistry());
    }

    @Autowired
    public TaskRealtimeMetrics(ObjectProvider<MeterRegistry> meterRegistries) {
        this(meterRegistries.getIfAvailable(SimpleMeterRegistry::new));
    }

    public TaskRealtimeMetrics(MeterRegistry registry) {
        committedEvents = counter(registry, "task_realtime_committed_events");
        rejectedSubscriptions = counter(registry, "task_realtime_rejected_subscriptions");
        emittedEnvelopes = counter(registry, "task_realtime_emitted_envelopes");
        deliveryFailures = counter(registry, "task_realtime_delivery_failures");
        publisherRejections = counter(registry, "task_realtime_publisher_rejections");
        rejectedAuthentications = counter(registry, "task_realtime_rejected_authentications");

    }

    public void recordCommittedEvent() {
        committedEvents.increment();
    }

    public void recordRejectedSubscription() {
        rejectedSubscriptions.increment();
    }

    public void recordEnvelopeEmitted() {
        emittedEnvelopes.increment();
    }

    public void recordDeliveryFailure() {
        deliveryFailures.increment();
    }

    public void recordPublisherRejection() {
        publisherRejections.increment();
    }

    public void recordRejectedAuthentication() {
        rejectedAuthentications.increment();
    }

    public long getCommittedEvents() {
        return (long) committedEvents.count();
    }

    public long getRejectedSubscriptions() {
        return (long) rejectedSubscriptions.count();
    }

    public long getEmittedEnvelopes() {
        return (long) emittedEnvelopes.count();
    }

    public long getDeliveryFailures() {
        return (long) deliveryFailures.count();
    }

    public long getPublisherRejections() {
        return (long) publisherRejections.count();
    }

    public long getRejectedAuthentications() {
        return (long) rejectedAuthentications.count();
    }

    private Counter counter(MeterRegistry registry, String name) {
        return Counter.builder(name)
                .description("Task realtime " + name.substring("task_realtime_".length()))
                .register(registry);
    }
}
