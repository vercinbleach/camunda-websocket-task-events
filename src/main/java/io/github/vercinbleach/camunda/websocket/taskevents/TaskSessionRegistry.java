package io.github.vercinbleach.camunda.websocket.taskevents;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

@Component
public class TaskSessionRegistry {
    private static final Logger logger = LoggerFactory.getLogger(TaskSessionRegistry.class);
    private static final String TASK_DESTINATION = "/user/queue/task-events";

    private final RealtimeProperties properties;
    private final Clock clock;
    private final ScheduledExecutorService scheduler;
    private final Semaphore sessionPermits;
    private final ConcurrentMap<String, SessionState> sessions = new ConcurrentHashMap<>();

    public TaskSessionRegistry(RealtimeProperties properties) {
        this(properties, Clock.systemUTC(), new SimpleMeterRegistry());
    }

    @Autowired
    public TaskSessionRegistry(RealtimeProperties properties, ObjectProvider<MeterRegistry> meterRegistries) {
        this(properties, Clock.systemUTC(), meterRegistries.getIfAvailable(SimpleMeterRegistry::new));
    }

    TaskSessionRegistry(RealtimeProperties properties, MeterRegistry meterRegistry) {
        this(properties, Clock.systemUTC(), meterRegistry);
    }

    TaskSessionRegistry(RealtimeProperties properties, Clock clock, MeterRegistry meterRegistry) {
        this.properties = properties;
        this.clock = clock;
        this.sessionPermits = new Semaphore(properties.getWebsocket().getMaxSessions(), true);
        ScheduledThreadPoolExecutor expiryScheduler = new ScheduledThreadPoolExecutor(1, runnable -> {
            Thread thread = new Thread(runnable, "task-ws-credential-expiry");
            thread.setDaemon(true);
            return thread;
        });
        expiryScheduler.setRemoveOnCancelPolicy(true);
        this.scheduler = expiryScheduler;
        Gauge.builder("task_realtime_active_transports", this, TaskSessionRegistry::getSessionCount)
                .description("Open realtime WebSocket transports")
                .register(meterRegistry);
        Gauge.builder("task_realtime_active_subscriptions", this, TaskSessionRegistry::getTotalSubscriptionCount)
                .description("Active task-event subscriptions in realtime sessions")
                .register(meterRegistry);
    }

    public boolean registerTransportSession(WebSocketSession webSocketSession) {
        String sessionId = webSocketSession.getId();
        if (sessionId == null || sessionId.isBlank()) {
            closeQuietly(webSocketSession, CloseStatus.POLICY_VIOLATION);
            return false;
        }
        if (sessions.containsKey(sessionId)) {
            closeQuietly(webSocketSession, CloseStatus.POLICY_VIOLATION);
            return false;
        }
        if (!sessionPermits.tryAcquire()) {
            closeQuietly(webSocketSession, CloseStatus.SERVICE_OVERLOAD);
            return false;
        }

        SessionState state = new SessionState(webSocketSession);
        if (sessions.putIfAbsent(sessionId, state) != null) {
            sessionPermits.release();
            closeQuietly(webSocketSession, CloseStatus.POLICY_VIOLATION);
            return false;
        }
        return true;
    }

    public boolean bindCredentialExpiry(String sessionId, Instant expiresAt) {
        SessionState state = sessions.get(sessionId);
        if (state == null) {
            return false;
        }
        long delayMillis = expiresAt == null ? -1 : Duration.between(clock.instant(), expiresAt).toMillis();
        if (expiresAt != null && delayMillis <= 0) {
            close(sessionId, CloseStatus.POLICY_VIOLATION);
            return false;
        }
        synchronized (state) {
            if (sessions.get(sessionId) != state || state.credentialsBound) {
                return false;
            }
            state.credentialsBound = true;
            if (expiresAt != null) {
                state.expiryTask = scheduler.schedule(
                        () -> closeExpired(sessionId, state, expiresAt),
                        delayMillis,
                        TimeUnit.MILLISECONDS);
            }
            return true;
        }
    }

    public boolean registerSubscription(String sessionId, String subscriptionId, String destination) {
        if (!TASK_DESTINATION.equals(destination) || subscriptionId == null || subscriptionId.isBlank()) {
            return false;
        }
        SessionState state = sessions.get(sessionId);
        if (state == null) {
            return false;
        }
        synchronized (state) {
            if (sessions.get(sessionId) != state) {
                return false;
            }
            if (state.subscriptions.size() >= properties.getWebsocket().getMaxSubscriptionsPerSession()
                    || !state.subscriptions.add(subscriptionId)) {
                return false;
            }
            return true;
        }
    }

    public boolean unregisterSubscription(String sessionId, String subscriptionId) {
        if (subscriptionId == null || subscriptionId.isBlank()) {
            return false;
        }
        SessionState state = sessions.get(sessionId);
        if (state == null) {
            return false;
        }
        synchronized (state) {
            return sessions.get(sessionId) == state
                    && state.subscriptions.remove(subscriptionId);
        }
    }

    public void remove(String sessionId) {
        removeState(sessionId);
    }

    public void close(String sessionId, CloseStatus status) {
        SessionState state = removeState(sessionId);
        if (state == null) {
            return;
        }
        closeQuietly(state.webSocketSession, status);
    }

    public int getSessionCount() {
        return sessions.size();
    }

    public int getSubscriptionCount(String sessionId) {
        SessionState state = sessions.get(sessionId);
        return state == null ? 0 : state.subscriptions.size();
    }

    public int getTotalSubscriptionCount() {
        return sessions.values().stream()
                .mapToInt(state -> state.subscriptions.size())
                .sum();
    }

    @PreDestroy
    public void shutdown() {
        for (String sessionId : sessions.keySet()) {
            remove(sessionId);
        }
        scheduler.shutdownNow();
    }

    private void closeExpired(String sessionId, SessionState expectedState, Instant expectedExpiry) {
        synchronized (expectedState) {
            if (sessions.get(sessionId) != expectedState) {
                return;
            }
            long remainingMillis = Duration.between(clock.instant(), expectedExpiry).toMillis();
            if (remainingMillis > 0) {
                expectedState.expiryTask = scheduler.schedule(
                        () -> closeExpired(sessionId, expectedState, expectedExpiry),
                        remainingMillis,
                        TimeUnit.MILLISECONDS);
                return;
            }
        }
        close(sessionId, CloseStatus.POLICY_VIOLATION);
    }

    private SessionState removeState(String sessionId) {
        SessionState state = sessions.remove(sessionId);
        if (state == null) {
            return null;
        }
        synchronized (state) {
            if (state.expiryTask != null) {
                state.expiryTask.cancel(false);
                state.expiryTask = null;
            }
        }
        sessionPermits.release();
        return state;
    }

    private void closeQuietly(WebSocketSession webSocketSession, CloseStatus status) {
        try {
            if (webSocketSession.isOpen()) {
                webSocketSession.close(status);
            }
        } catch (IOException | RuntimeException exception) {
            logger.debug("WebSocket session close failed without session or payload details");
        }
    }

    private static final class SessionState {
        private final WebSocketSession webSocketSession;
        private final Set<String> subscriptions = ConcurrentHashMap.newKeySet();
        private boolean credentialsBound;
        private ScheduledFuture<?> expiryTask;

        private SessionState(WebSocketSession webSocketSession) {
            this.webSocketSession = webSocketSession;
        }
    }
}
