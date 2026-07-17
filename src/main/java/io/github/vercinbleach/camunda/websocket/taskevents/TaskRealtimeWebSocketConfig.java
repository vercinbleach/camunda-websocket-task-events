package io.github.vercinbleach.camunda.websocket.taskevents;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration;

@Configuration
@EnableWebSocketMessageBroker
@Order(Ordered.HIGHEST_PRECEDENCE + 99)
public class TaskRealtimeWebSocketConfig implements WebSocketMessageBrokerConfigurer {
    private final RealtimeProperties properties;
    private final TaskRealtimeHandshakeHandler handshakeHandler;
    private final TaskRealtimeHandshakeInterceptor handshakeInterceptor;
    private final TaskRealtimeProtocolInterceptor protocolInterceptor;
    private final TaskRealtimeCredentialAuthenticationInterceptor authenticationInterceptor;
    private final TaskSessionLimitsInterceptor sessionLimitsInterceptor;
    private final TaskWebSocketSessionTracker sessionTracker;

    public TaskRealtimeWebSocketConfig(
            RealtimeProperties properties,
            TaskRealtimeHandshakeHandler handshakeHandler,
            ObjectProvider<TaskRealtimeHandshakeAuthenticator> handshakeAuthenticators,
            TaskRealtimeProtocolInterceptor protocolInterceptor,
            ObjectProvider<TaskRealtimeCredentialAuthenticator> authenticators,
            TaskSessionRegistry sessionRegistry,
            TaskRealtimeMetrics metrics,
            TaskSessionLimitsInterceptor sessionLimitsInterceptor,
            TaskWebSocketSessionTracker sessionTracker) {
        this.properties = properties;
        this.handshakeHandler = handshakeHandler;
        this.handshakeInterceptor = new TaskRealtimeHandshakeInterceptor(
                handshakeAuthenticators.orderedStream().toList());
        this.protocolInterceptor = protocolInterceptor;
        this.authenticationInterceptor = new TaskRealtimeCredentialAuthenticationInterceptor(
                authenticators.orderedStream().toList(), sessionRegistry, metrics);
        this.sessionLimitsInterceptor = sessionLimitsInterceptor;
        this.sessionTracker = sessionTracker;
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/queue")
                .setHeartbeatValue(new long[]{
                        properties.getWebsocket().getHeartbeat().toMillis(),
                        properties.getWebsocket().getHeartbeat().toMillis()})
                .setTaskScheduler(stompBrokerTaskScheduler());
        registry.setApplicationDestinationPrefixes("/app");
        registry.setUserDestinationPrefix("/user");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        var endpoint = registry.addEndpoint(properties.getWebsocket().getEndpoint())
                .addInterceptors(handshakeInterceptor)
                .setHandshakeHandler(handshakeHandler);
        if (!properties.getWebsocket().getAllowedOrigins().isEmpty()) {
            endpoint.setAllowedOrigins(properties.getWebsocket().getAllowedOrigins().toArray(String[]::new));
        }
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(
                protocolInterceptor,
                authenticationInterceptor,
                sessionLimitsInterceptor);
    }

    @Override
    public void configureWebSocketTransport(WebSocketTransportRegistration registration) {
        registration.setMessageSizeLimit(properties.getWebsocket().getMaxMessageSize());
        registration.setSendBufferSizeLimit(properties.getWebsocket().getSendBufferSize());
        registration.setSendTimeLimit(Math.toIntExact(properties.getWebsocket().getSendTimeLimit().toMillis()));
        registration.setTimeToFirstMessage(Math.toIntExact(properties.getWebsocket().getFirstConnectTimeout().toMillis()));
        registration.addDecoratorFactory(sessionTracker);
    }

    @Bean
    public TaskScheduler stompBrokerTaskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("stomp-heartbeat-");
        scheduler.setDaemon(true);
        return scheduler;
    }
}
