package io.github.vercinbleach.camunda.websocket.taskevents;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class TaskSessionLimitsInterceptorTest {
    @Mock
    private TaskSessionRegistry sessionRegistry;

    @Mock
    private TaskRealtimeMetrics metrics;

    @Mock
    private MessageChannel channel;

    @Test
    void leavesTransportOwnershipToTheWebSocketCloseTrackerOnDisconnect() {
        TaskSessionLimitsInterceptor interceptor = new TaskSessionLimitsInterceptor(sessionRegistry, metrics);
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.DISCONNECT);
        accessor.setSessionId("session-1");
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        assertThat(interceptor.preSend(message, channel)).isSameAs(message);
        verifyNoInteractions(sessionRegistry, metrics);
    }
}
