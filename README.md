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
- security: automatically inherited from Spring Security or Camunda REST;
- origin policy: Spring's same-origin default;
- subscription: `/user/queue/task-events`;
- client `SEND`: denied;
- event delivery: best-effort invalidation after commit;
- Camunda eventing: `camunda.bpm.eventing.skippable=false` supplied at low
  precedence and validated fail-closed.

If HTTP security established a `Principal`, Spring carries it into the
WebSocket session. Stateless browser clients can instead send their existing
bearer token in STOMP `CONNECT`; the library delegates validation to the
application's existing Spring Security beans. If the application deliberately
permits anonymous access, the library creates an opaque, session-local routing
principal so the private queue still works. That principal grants no Camunda or
REST permissions.

## Browser client

Using `@stomp/stompjs`:

```ts
import { Client } from '@stomp/stompjs';

const client = new Client({
  brokerURL: 'wss://example.com/ws/task-events',
  // Omit connectHeaders for session-based or deliberately anonymous setups.
  connectHeaders: { Authorization: `Bearer ${accessToken}` },
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

## Zero-configuration security

There is no `authentication.provider` property and the library does not contain
Keycloak-, issuer-, audience- or claim-specific configuration. It discovers and
reuses the security mechanism already configured by the application:

| Existing application setup | Reused automatically |
| --- | --- |
| HTTP session, form login, Basic or OAuth2 login | Handshake `Principal` |
| Spring Resource Server JWT | `JwtDecoder` and an exposed `JwtAuthenticationConverter` bean |
| Spring Resource Server opaque token | `OpaqueTokenIntrospector` and an exposed converter bean |
| Camunda REST auth | `AuthenticationProvider` bean or the provider class registered on `ProcessEngineAuthenticationFilter` |
| No authentication | Ephemeral routing-only principal |

Spring's normal WebSocket behavior transfers an HTTP principal to the session,
but browsers cannot add arbitrary HTTP headers to the WebSocket handshake and
Spring intentionally ignores STOMP authentication headers by default. The
resource-server adapters therefore process bearer credentials from `CONNECT`
through the same decoder, validators and exposed converter as the REST
application. The standard Spring converter is used when the application did not
expose one. Token expiry closes the WebSocket session.

The Camunda adapter is used only when the HTTP handshake itself carries an
`Authorization` header and Spring has not already established a principal. It
supports both a unique `AuthenticationProvider` bean and the standard
`authentication-provider` init parameter used by Camunda's REST filter. A
unique/default `ProcessEngine` is required because a WebSocket URL does not
address an engine name.

Credentials are fail-closed: invalid or unsupported credentials are rejected;
the library never retries through another mechanism and never downgrades them
to anonymous. If JWT and opaque-token support are both present without a
higher-priority custom `TaskRealtimeCredentialAuthenticator`, bearer
authentication is rejected as ambiguous instead of guessing the token format.

For a genuinely custom mechanism, expose one ordered
`TaskRealtimeCredentialAuthenticator` bean for STOMP credentials or one
`TaskRealtimeHandshakeAuthenticator` bean for HTTP-handshake credentials. The
standard cases require no such bean. When several mechanisms support the same
credential, the custom implementation must override `Ordered#getOrder()` with a
lower value to override the broad Camunda REST or resource-server adapter.

This design follows [Spring WebSocket authentication](https://docs.spring.io/spring-framework/reference/web/websocket/stomp/authentication.html),
[Spring token authentication for STOMP](https://docs.spring.io/spring-framework/reference/web/websocket/stomp/authentication-token-based.html),
and Spring Security's
[`AuthenticationManager` architecture](https://docs.spring.io/spring-security/reference/servlet/authentication/architecture.html).
Camunda's integration point is the official
[`AuthenticationProvider`](https://github.com/camunda/camunda-bpm-platform/blob/7.24.0/engine-rest/engine-rest/src/main/java/org/camunda/bpm/engine/rest/security/auth/AuthenticationProvider.java)
configured by
[`ProcessEngineAuthenticationFilter`](https://github.com/camunda/camunda-bpm-platform/blob/7.24.0/engine-rest/engine-rest/src/main/java/org/camunda/bpm/engine/rest/security/auth/ProcessEngineAuthenticationFilter.java).

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
subscriptions, rejected authentication, delivery failures, publisher rejection, active
transports and active subscriptions.

## Build

```bash
./mvnw verify
```

## License

Apache License 2.0.
