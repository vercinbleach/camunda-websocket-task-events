package io.github.vercinbleach.camunda.websocket.taskevents;

import org.springframework.messaging.simp.stomp.StompHeaderAccessor;

public interface TaskRealtimeAuthenticationProvider {
    String id();

    TaskRealtimeAuthentication authenticate(StompHeaderAccessor connectHeaders);
}
