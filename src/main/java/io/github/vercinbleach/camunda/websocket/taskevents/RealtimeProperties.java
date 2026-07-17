package io.github.vercinbleach.camunda.websocket.taskevents;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "camunda.websocket.task-events")
public class RealtimeProperties {

    private final Websocket websocket = new Websocket();

    public Websocket getWebsocket() {
        return websocket;
    }

    @PostConstruct
    void validate() {
        websocket.validate();
    }

    public static class Websocket {
        private String endpoint = "/ws/task-events";
        private List<String> allowedOrigins = new ArrayList<>();
        private Duration firstConnectTimeout = Duration.ofSeconds(10);
        private int maxMessageSize = 64 * 1024;
        private int sendBufferSize = 512 * 1024;
        private Duration sendTimeLimit = Duration.ofSeconds(10);
        private int maxSessions = 500;
        private int maxSubscriptionsPerSession = 1;
        private Duration heartbeat = Duration.ofSeconds(10);

        public String getEndpoint() {
            return endpoint;
        }

        public void setEndpoint(String endpoint) {
            this.endpoint = endpoint;
        }

        public List<String> getAllowedOrigins() {
            return allowedOrigins;
        }

        public void setAllowedOrigins(List<String> allowedOrigins) {
            this.allowedOrigins = allowedOrigins == null ? new ArrayList<>() : new ArrayList<>(allowedOrigins);
        }

        public Duration getFirstConnectTimeout() {
            return firstConnectTimeout;
        }

        public void setFirstConnectTimeout(Duration firstConnectTimeout) {
            this.firstConnectTimeout = firstConnectTimeout;
        }

        public int getMaxMessageSize() {
            return maxMessageSize;
        }

        public void setMaxMessageSize(int maxMessageSize) {
            this.maxMessageSize = maxMessageSize;
        }

        public int getSendBufferSize() {
            return sendBufferSize;
        }

        public void setSendBufferSize(int sendBufferSize) {
            this.sendBufferSize = sendBufferSize;
        }

        public Duration getSendTimeLimit() {
            return sendTimeLimit;
        }

        public void setSendTimeLimit(Duration sendTimeLimit) {
            this.sendTimeLimit = sendTimeLimit;
        }

        public int getMaxSessions() {
            return maxSessions;
        }

        public void setMaxSessions(int maxSessions) {
            this.maxSessions = maxSessions;
        }

        public int getMaxSubscriptionsPerSession() {
            return maxSubscriptionsPerSession;
        }

        public void setMaxSubscriptionsPerSession(int maxSubscriptionsPerSession) {
            this.maxSubscriptionsPerSession = maxSubscriptionsPerSession;
        }

        public Duration getHeartbeat() {
            return heartbeat;
        }

        public void setHeartbeat(Duration heartbeat) {
            this.heartbeat = heartbeat;
        }

        private void validate() {
            if (endpoint == null || endpoint.isBlank() || !endpoint.startsWith("/") || endpoint.contains("*")) {
                throw new IllegalArgumentException("task-events websocket endpoint must be an exact absolute path");
            }
            if (allowedOrigins.stream().anyMatch(origin ->
                    origin == null || origin.isBlank() || origin.contains("*"))) {
                throw new IllegalArgumentException("task-events allowed-origins must be an exact non-wildcard allowlist");
            }
            if (firstConnectTimeout == null || firstConnectTimeout.isZero() || firstConnectTimeout.isNegative()
                    || sendTimeLimit == null || sendTimeLimit.isZero() || sendTimeLimit.isNegative()
                    || heartbeat == null || heartbeat.isZero() || heartbeat.isNegative()) {
                throw new IllegalArgumentException("task-events websocket durations must be positive");
            }
            if (maxMessageSize <= 0 || sendBufferSize <= 0 || maxSessions <= 0 || maxSubscriptionsPerSession <= 0) {
                throw new IllegalArgumentException("task-events websocket limits must be positive");
            }
        }
    }

}
