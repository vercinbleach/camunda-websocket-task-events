package io.github.vercinbleach.camunda.websocket.taskevents;

import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class TaskRealtimeProtocolInterceptorTest {
    private final TaskRealtimeProtocolInterceptor interceptor = new TaskRealtimeProtocolInterceptor();
    private final MessageChannel channel = mock(MessageChannel.class);

    @Test
    void allowsOnlyThePrivateTaskEventSubscription() {
        Message<byte[]> allowed = message(StompCommand.SUBSCRIBE, "/user/queue/task-events", "sub-1");

        assertThat(interceptor.preSend(allowed, channel)).isSameAs(allowed);
        assertThatThrownBy(() -> interceptor.preSend(
                message(StompCommand.SUBSCRIBE, "/topic/tasks", "sub-2"), channel))
                .isInstanceOf(MessageDeliveryException.class);
    }

    @Test
    void deniesClientSendAndAllowsSessionLifecycleFrames() {
        assertThatThrownBy(() -> interceptor.preSend(
                message(StompCommand.SEND, "/app/tasks", null), channel))
                .isInstanceOf(MessageDeliveryException.class);

        for (StompCommand command : new StompCommand[]{
                StompCommand.CONNECT,
                StompCommand.DISCONNECT,
                StompCommand.UNSUBSCRIBE}) {
            Message<byte[]> message = message(command, null, "sub-1");
            assertThat(interceptor.preSend(message, channel)).isSameAs(message);
        }
    }

    @Test
    void allowsHeartbeatsWithoutAuthentication() {
        StompHeaderAccessor accessor = StompHeaderAccessor.createForHeartbeat();
        accessor.setSessionId("session-1");
        Message<byte[]> heartbeat = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        assertThat(interceptor.preSend(heartbeat, channel)).isSameAs(heartbeat);
    }

    private Message<byte[]> message(StompCommand command, String destination, String subscriptionId) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(command);
        accessor.setSessionId("session-1");
        accessor.setDestination(destination);
        accessor.setSubscriptionId(subscriptionId);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }
}
