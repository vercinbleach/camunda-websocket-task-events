package io.github.vercinbleach.camunda.websocket.taskevents;

import java.security.Principal;

/** Creates the transport principal after an external handshake provider authenticates a username. */
public interface TaskRealtimePrincipalFactory {
    Principal authenticated(String username);
}
