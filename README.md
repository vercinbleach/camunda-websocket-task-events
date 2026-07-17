# Camunda WebSocket Task Events

Authenticated, minimal task-list invalidations for Camunda 7 applications that
run with Spring Boot. Add one Maven dependency; the library registers a native
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

For a normal Spring-authenticated Camunda application, no library-specific Java
configuration is required. The defaults are:

- endpoint: `/ws/task-events`;
- authentication: the `Principal` established by the HTTP handshake;
- origin policy: Spring's same-origin default;
- subscription: `/user/queue/task-events`;
- client `SEND`: denied;
- event delivery: best-effort invalidation after commit;
- Camunda eventing: `camunda.bpm.eventing.skippable=false` supplied at low
  precedence and validated fail-closed.

The application must authenticate the WebSocket handshake. An anonymous
handshake can open the transport, but its STOMP `CONNECT` is rejected and
cannot subscribe.

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

## Stateless JWT mode

Browsers cannot add an arbitrary `Authorization` header to the HTTP WebSocket
upgrade. For stateless SPAs, the token can instead be sent as a native STOMP
`CONNECT` header:

```yaml
camunda:
  websocket:
    task-events:
      authentication:
        provider: stomp-bearer-jwt
```

This mode reuses the application's Spring Security `JwtDecoder`, including
whatever signature, issuer, audience or custom claim validators the application
configured. It also reuses an
application `JwtAuthenticationConverter` bean when one is available; otherwise
Spring Security's standard converter is used (`sub` is the principal claim).
There is no second JWT configuration inside this library.

Applications that use another principal claim should expose the same converter
used by their HTTP resource server:

```java
@Bean
JwtAuthenticationConverter jwtAuthenticationConverter() {
  JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
  converter.setPrincipalClaimName("preferred_username");
  return converter;
}
```

The client must set a fresh token for every connection:

```ts
client.beforeConnect = async () => {
  client.connectHeaders = {
    Authorization: `Bearer ${await currentAccessToken()}`,
  };
};
```

The application decoder remains the single authority for JWT validation. The
library requires `exp` so it can close the WebSocket when the token expires.

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

## Custom authentication

Implement one bean when neither built-in provider matches the application's
security model:

```java
@Component
class MyRealtimeAuthenticationProvider
        implements TaskRealtimeAuthenticationProvider {

  @Override
  public String id() {
    return "my-provider";
  }

  @Override
  public TaskRealtimeAuthentication authenticate(
          StompHeaderAccessor connectHeaders) {
    // Validate credentials and return a normalized Spring Authentication.
  }
}
```

```yaml
camunda.websocket.task-events.authentication.provider: my-provider
```

Exactly one bean must match the configured ID. Missing or duplicate providers
fail startup; the library never falls back to another authentication mechanism.

## Semantics

Camunda task events may be raised before the surrounding transaction commits.
This library consumes the Spring event with
`@TransactionalEventListener(AFTER_COMMIT)`, then coalesces bursts on a bounded
executor before broadcasting the invalidation to authenticated users.

Consequences:

- rollback produces no public invalidation;
- realtime failure never rolls back a Camunda command;
- delivery is not durable and has no replay;
- reconnect always triggers a REST reconciliation;
- REST and Camunda authorization remain the source of truth.

## Metrics

When Micrometer is available, the library registers counters and gauges with
the `task_realtime_` prefix for committed events, emitted envelopes, rejected
authentication/subscriptions, delivery failures, publisher rejection, active
transports and active subscriptions.

## Build

```bash
./mvnw verify
```

## License

Apache License 2.0.
