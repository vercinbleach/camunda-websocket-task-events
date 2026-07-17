package io.github.vercinbleach.camunda.websocket.taskevents;

import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TaskStompAuthorizationManagerTest {
    private final TaskStompAuthorizationManager manager = new TaskStompAuthorizationManager();
    private final Authentication authentication = UsernamePasswordAuthenticationToken.authenticated("demo", null, List.of());

    @Test
    void allowsOnlyTheUserTaskSubscription() {
        assertThat(manager.check(() -> authentication, message(StompCommand.SUBSCRIBE, "/user/queue/task-events", "sub-1")).isGranted())
                .isTrue();
        assertThat(manager.check(() -> authentication, message(StompCommand.SUBSCRIBE, "/topic/tasks", "sub-2")).isGranted())
                .isFalse();
        assertThat(manager.check(() -> authentication, message(StompCommand.SUBSCRIBE, "/queue/task-events", "sub-3")).isGranted())
                .isFalse();
    }

    @Test
    void deniesClientSendAndMessageFrames() {
        assertThat(manager.check(() -> authentication, message(StompCommand.SEND, "/app/tasks", null)).isGranted())
                .isFalse();
        assertThat(manager.check(() -> authentication, message(StompCommand.MESSAGE, "/queue/task-events", null)).isGranted())
                .isFalse();
    }

    @Test
    void requiresAuthenticationForSessionCommands() {
        for (StompCommand command : List.of(StompCommand.CONNECT, StompCommand.DISCONNECT, StompCommand.UNSUBSCRIBE)) {
            assertThat(manager.check(() -> authentication, message(command, null, "sub-1")).isGranted()).isTrue();
            assertThat(manager.check(() -> null, messageWithoutUser(command, null, "sub-1")).isGranted()).isFalse();
        }
    }

    @Test
    void allowsOnlyAuthenticatedHeartbeatMessages() {
        assertThat(manager.check(() -> authentication, heartbeat(authentication)).isGranted()).isTrue();
        assertThat(manager.check(() -> null, heartbeat(null)).isGranted()).isFalse();
    }

    private Message<byte[]> message(StompCommand command, String destination, String subscriptionId) {
        return message(command, destination, subscriptionId, authentication);
    }

    private Message<byte[]> messageWithoutUser(StompCommand command, String destination, String subscriptionId) {
        return message(command, destination, subscriptionId, null);
    }

    private Message<byte[]> message(StompCommand command,
                                    String destination,
                                    String subscriptionId,
                                    Authentication user) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(command);
        accessor.setSessionId("session-1");
        accessor.setUser(user);
        accessor.setDestination(destination);
        accessor.setSubscriptionId(subscriptionId);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    private Message<byte[]> heartbeat(Authentication user) {
        StompHeaderAccessor accessor = StompHeaderAccessor.createForHeartbeat();
        accessor.setSessionId("session-1");
        accessor.setUser(user);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }
}
