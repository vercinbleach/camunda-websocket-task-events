package io.github.vercinbleach.camunda.websocket.taskevents;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.security.messaging.access.intercept.AuthorizationChannelInterceptor;
import org.springframework.security.messaging.context.SecurityContextChannelInterceptor;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration;

@Configuration
@EnableWebSocketMessageBroker
public class TaskRealtimeWebSocketConfig implements WebSocketMessageBrokerConfigurer {
    private final RealtimeProperties properties;
    private final TaskRealtimeAuthenticationInterceptor authenticationInterceptor;
    private final SecurityContextChannelInterceptor securityContextInterceptor;
    private final AuthorizationChannelInterceptor authorizationInterceptor;
    private final TaskSessionLimitsInterceptor sessionLimitsInterceptor;
    private final TaskWebSocketSessionTracker sessionTracker;

    public TaskRealtimeWebSocketConfig(
            RealtimeProperties properties,
            TaskRealtimeAuthenticationInterceptor authenticationInterceptor,
            SecurityContextChannelInterceptor securityContextInterceptor,
            AuthorizationChannelInterceptor authorizationInterceptor,
            TaskSessionLimitsInterceptor sessionLimitsInterceptor,
            TaskWebSocketSessionTracker sessionTracker) {
        this.properties = properties;
        this.authenticationInterceptor = authenticationInterceptor;
        this.securityContextInterceptor = securityContextInterceptor;
        this.authorizationInterceptor = authorizationInterceptor;
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
        var endpoint = registry.addEndpoint(properties.getWebsocket().getEndpoint());
        if (!properties.getWebsocket().getAllowedOrigins().isEmpty()) {
            endpoint.setAllowedOrigins(properties.getWebsocket().getAllowedOrigins().toArray(String[]::new));
        }
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(
                authenticationInterceptor,
                securityContextInterceptor,
                authorizationInterceptor,
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
