package io.github.vercinbleach.camunda.websocket.taskevents;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TaskSessionRegistryTest {
    private TaskSessionRegistry registry;

    @AfterEach
    void tearDown() {
        if (registry != null) {
            registry.shutdown();
        }
    }

    @Test
    void enforcesOneSubscriptionAndClosesExpiredJwtSession() throws Exception {
        RealtimeProperties properties = properties(1, 1);
        registry = new TaskSessionRegistry(properties);
        WebSocketSession session = session("session-1");

        assertThat(registry.registerTransportSession(session)).isTrue();
        assertThat(registry.authenticate("session-1", Instant.now().plusMillis(250))).isTrue();
        assertThat(registry.registerSubscription("session-1", "sub-1", "/user/queue/task-events")).isTrue();
        assertThat(registry.registerSubscription("session-1", "sub-2", "/user/queue/task-events")).isFalse();
        assertThat(registry.getSubscriptionCount("session-1")).isEqualTo(1);

        verify(session, org.mockito.Mockito.timeout(1000)).close(eq(CloseStatus.POLICY_VIOLATION));
        assertThat(registry.getSessionCount()).isZero();
        assertThat(registry.registerTransportSession(session("session-2"))).isTrue();
    }

    @Test
    void rejectsAdditionalTransportSessionWhenLimitIsReached() throws Exception {
        RealtimeProperties properties = properties(1, 1);
        registry = new TaskSessionRegistry(properties);
        WebSocketSession first = session("session-1");
        WebSocketSession second = session("session-2");

        assertThat(registry.registerTransportSession(first)).isTrue();
        assertThat(registry.registerTransportSession(second)).isFalse();

        verify(second).close(eq(CloseStatus.SERVICE_OVERLOAD));
    }

    @Test
    void acceptsAnAuthenticatedPrincipalWithoutAProviderManagedExpiry() {
        RealtimeProperties properties = properties(1, 1);
        registry = new TaskSessionRegistry(properties);
        WebSocketSession session = session("session-1");

        assertThat(registry.registerTransportSession(session)).isTrue();
        assertThat(registry.authenticate("session-1", null)).isTrue();
        assertThat(registry.authenticate("session-1", null)).isFalse();
        assertThat(registry.getSessionCount()).isEqualTo(1);
    }

    @Test
    void releasesSessionPermitExactlyOnce() throws Exception {
        RealtimeProperties properties = properties(1, 1);
        registry = new TaskSessionRegistry(properties);
        WebSocketSession first = session("session-1");
        WebSocketSession second = session("session-2");
        WebSocketSession third = session("session-3");

        assertThat(registry.registerTransportSession(first)).isTrue();
        registry.remove("session-1");
        registry.remove("session-1");

        assertThat(registry.registerTransportSession(second)).isTrue();
        assertThat(registry.registerTransportSession(third)).isFalse();
        verify(third).close(eq(CloseStatus.SERVICE_OVERLOAD));
    }

    @Test
    void neverExceedsMaxSessionsUnderConcurrentRegistrations() throws Exception {
        int maxSessions = 4;
        int attempts = 32;
        RealtimeProperties properties = properties(maxSessions, 1);
        registry = new TaskSessionRegistry(properties);
        ExecutorService executor = Executors.newFixedThreadPool(attempts);
        CountDownLatch ready = new CountDownLatch(attempts);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Boolean>> registrations = new ArrayList<>();

        try {
            for (int index = 0; index < attempts; index++) {
                String sessionId = "session-" + index;
                registrations.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    return registry.registerTransportSession(session(sessionId));
                }));
            }

            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            long accepted = 0;
            for (Future<Boolean> registration : registrations) {
                if (registration.get(5, TimeUnit.SECONDS)) {
                    accepted++;
                }
            }

            assertThat(accepted).isEqualTo(maxSessions);
            assertThat(registry.getSessionCount()).isEqualTo(maxSessions);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void rejectsSecondConnectAndKeepsTheOriginalExpiry() throws Exception {
        RealtimeProperties properties = properties(1, 1);
        registry = new TaskSessionRegistry(properties);
        WebSocketSession session = session("session-1");

        assertThat(registry.registerTransportSession(session)).isTrue();
        assertThat(registry.authenticate("session-1", Instant.now().plusMillis(250))).isTrue();
        assertThat(registry.authenticate("session-1", Instant.now().plusSeconds(30))).isFalse();

        verify(session, org.mockito.Mockito.timeout(1000).times(1)).close(eq(CloseStatus.POLICY_VIOLATION));
        assertThat(registry.getSessionCount()).isZero();
    }

    @Test
    void exposesSessionAndSubscriptionGaugesThroughMicrometer() {
        RealtimeProperties properties = properties(1, 1);
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        registry = new TaskSessionRegistry(properties, java.time.Clock.systemUTC(), meterRegistry);

        assertThat(meterRegistry.get("task_realtime_active_transports").gauge().value()).isZero();
        assertThat(meterRegistry.get("task_realtime_active_subscriptions").gauge().value()).isZero();
    }

    private RealtimeProperties properties(int maxSessions, int maxSubscriptions) {
        RealtimeProperties properties = new RealtimeProperties();
        properties.getWebsocket().setMaxSessions(maxSessions);
        properties.getWebsocket().setMaxSubscriptionsPerSession(maxSubscriptions);
        properties.getWebsocket().setFirstConnectTimeout(java.time.Duration.ofSeconds(2));
        return properties;
    }

    private WebSocketSession session(String id) {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn(id);
        when(session.isOpen()).thenReturn(true);
        return session;
    }
}
