package io.github.vercinbleach.camunda.websocket.taskevents;

import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.user.SimpUser;
import org.springframework.messaging.simp.user.SimpUserRegistry;

import java.util.Set;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TaskEventBroadcasterTest {
    @Test
    void fansOutTheSameMinimalEnvelopeToEveryConnectedUser() {
        SimpMessagingTemplate template = mock(SimpMessagingTemplate.class);
        SimpUserRegistry userRegistry = mock(SimpUserRegistry.class);
        TaskRealtimeMetrics metrics = new TaskRealtimeMetrics();
        SimpUser first = mock(SimpUser.class);
        SimpUser second = mock(SimpUser.class);
        when(first.getName()).thenReturn("first");
        when(second.getName()).thenReturn("second");
        when(userRegistry.getUsers()).thenReturn(Set.of(first, second));

        TaskEventBroadcaster broadcaster = new TaskEventBroadcaster(
                template,
                userRegistry,
                metrics);

        broadcaster.publish();

        verify(template).convertAndSendToUser(eq("first"), eq("/queue/task-events"), any(TaskRealtimeEnvelope.class));
        verify(template).convertAndSendToUser(eq("second"), eq("/queue/task-events"), any(TaskRealtimeEnvelope.class));
    }
}
