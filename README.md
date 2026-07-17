# Camunda WebSocket Task Events

Minimal task-list invalidations for Camunda 7 applications that run with Spring
Boot. Add one Maven dependency; the library registers a native
WebSocket/STOMP endpoint and publishes an invalidation only after the Camunda
transaction commits.

The WebSocket never becomes a second task API. It carries no task IDs,
variables, assignees or business data. Clients receive an invalidation and
reload authorized state from Camunda REST.

## Status

Pre-release. The first compatibility target is:

| Component | Version |
| --- | --- |
| Java | 17+ |
| Camunda Platform 7 | 7.24.x |
| Spring Boot | 3.5.x |
| Spring Framework | 6.2.x |

The artifact is not yet published to Maven Central. Until the first release,
install it locally with `./mvnw install`.

## Install

```xml
<dependency>
  <groupId>io.github.vercinbleach</groupId>
  <artifactId>camunda-websocket-task-events</artifactId>
  <version>0.1.0-SNAPSHOT</version>
</dependency>
```

No authentication choice or library-specific Java configuration is required.
The defaults are:

- endpoint: `/ws/task-events`;
- security: inherited from the HTTP WebSocket handshake;
- origin policy: Spring's same-origin default;
- subscription: `/user/queue/task-events`;
- client `SEND`: denied;
- event delivery: best-effort invalidation after commit;
- Camunda eventing: `camunda.bpm.eventing.skippable=false` supplied at low
  precedence and validated fail-closed.

If HTTP security established a `Principal`, Spring carries it into the
WebSocket session. If the application permits anonymous access to the endpoint,
the library creates an opaque, session-local routing principal so the private
queue still works. That principal grants no Camunda or REST permissions.

## Browser client

Using `@stomp/stompjs`:

```ts
import { Client } from '@stomp/stompjs';

const client = new Client({
  brokerURL: 'wss://example.com/ws/task-events',
  reconnectDelay: 1000,
  heartbeatIncoming: 10000,
  heartbeatOutgoing: 10000,
});

client.onConnect = () => {
  client.subscribe('/user/queue/task-events', () => {
    void reloadTasksFromRest();
  });
  void reloadTasksFromRest();
};

client.activate();
```

The public envelope is intentionally small:

```json
{"schemaVersion":1,"type":"TASKS_INVALIDATED"}
```

## Security inheritance

The library contains no Basic, OAuth2, JWT, Keycloak or Camunda authentication
provider. The application's existing HTTP filters decide whether the handshake
to `/ws/task-events` is allowed. Any mechanism that exposes a
`HttpServletRequest` principal is inherited automatically by Spring WebSocket.

Camunda's REST `AuthenticationProvider` is deliberately not copied or invoked.
It belongs to `ProcessEngineAuthenticationFilter`, authenticates an engine REST
request and clears the engine identity after that request. It is not a global
authentication manager for other transports.

This follows [Spring WebSocket authentication](https://docs.spring.io/spring-framework/reference/web/websocket/stomp/authentication.html),
which transfers the HTTP request principal to the WebSocket session. The REST
boundary can be seen directly in Camunda 7's
[`ProcessEngineAuthenticationFilter`](https://github.com/camunda/camunda-bpm-platform/blob/7.24.0/engine-rest/engine-rest/src/main/java/org/camunda/bpm/engine/rest/security/auth/ProcessEngineAuthenticationFilter.java).

For browser-based stateless OAuth2, an application may allow the WebSocket
handshake without credentials. The notification contains no task or business
data and every reload still goes through the application's existing REST
security. Applications that consider workflow activity timing sensitive should
protect the handshake endpoint. The extension never interprets STOMP
`Authorization`, `login` or `passcode` headers.

## Configuration

```yaml
camunda:
  websocket:
    task-events:
      websocket:
        endpoint: /ws/task-events
        allowed-origins:
          - https://example.com
        first-connect-timeout: 10s
        heartbeat: 10s
        max-sessions: 500
        max-subscriptions-per-session: 1
        max-message-size: 65536
        send-buffer-size: 524288
        send-time-limit: 10s
```

Origins are exact values. Wildcards are rejected. Leaving the list empty keeps
Spring's same-origin behavior.

## Semantics

Camunda task events may be raised before the surrounding transaction commits.
This library consumes the Spring event with
`@TransactionalEventListener(AFTER_COMMIT)`, then coalesces bursts on a bounded
executor before broadcasting the invalidation to connected private sessions.

Consequences:

- rollback produces no public invalidation;
- realtime failure never rolls back a Camunda command;
- delivery is not durable and has no replay;
- reconnect always triggers a REST reconciliation;
- REST and Camunda authorization remain the source of truth.

## Metrics

When Micrometer is available, the library registers counters and gauges with
the `task_realtime_` prefix for committed events, emitted envelopes, rejected
subscriptions, delivery failures, publisher rejection, active
transports and active subscriptions.

## Build

```bash
./mvnw verify
```

## License

Apache License 2.0.
