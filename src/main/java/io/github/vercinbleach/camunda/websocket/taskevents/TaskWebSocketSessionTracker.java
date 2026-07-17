package io.github.vercinbleach.camunda.websocket.taskevents;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.WebSocketHandlerDecorator;
import org.springframework.web.socket.handler.WebSocketHandlerDecoratorFactory;

@Component
public class TaskWebSocketSessionTracker implements WebSocketHandlerDecoratorFactory {
    private final TaskSessionRegistry sessionRegistry;

    public TaskWebSocketSessionTracker(TaskSessionRegistry sessionRegistry) {
        this.sessionRegistry = sessionRegistry;
    }

    @Override
    public WebSocketHandler decorate(WebSocketHandler handler) {
        return new WebSocketHandlerDecorator(handler) {
            @Override
            public void afterConnectionEstablished(WebSocketSession session) throws Exception {
                if (!sessionRegistry.registerTransportSession(session)) {
                    return;
                }
                try {
                    super.afterConnectionEstablished(session);
                } catch (Exception exception) {
                    sessionRegistry.remove(session.getId());
                    throw exception;
                }
            }

            @Override
            public void afterConnectionClosed(WebSocketSession session,
                                               org.springframework.web.socket.CloseStatus closeStatus) throws Exception {
                try {
                    super.afterConnectionClosed(session, closeStatus);
                } finally {
                    sessionRegistry.remove(session.getId());
                }
            }
        };
    }
}
