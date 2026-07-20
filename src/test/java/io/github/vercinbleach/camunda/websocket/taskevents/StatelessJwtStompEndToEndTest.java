package io.github.vercinbleach.camunda.websocket.taskevents;

import org.camunda.bpm.spring.boot.starter.CamundaBpmAutoConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.springframework.web.socket.WebSocketHttpHeaders;

import java.lang.reflect.Type;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        classes = StatelessJwtStompEndToEndTest.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class StatelessJwtStompEndToEndTest {
    @LocalServerPort
    private int port;

    @jakarta.annotation.Resource
    private SimpMessagingTemplate messagingTemplate;

    @jakarta.annotation.Resource
    private SimpUserRegistry userRegistry;

    @Test
    void authenticatesConnectWithTheApplicationsJwtBeansAndReceivesThePrivateInvalidation()
            throws Exception {
        WebSocketStompClient client = new WebSocketStompClient(new StandardWebSocketClient());
        StompHeaders connectHeaders = new StompHeaders();
        connectHeaders.set("Authorization", "Bearer e2e-token");
        StompSession session = client.connectAsync(
                        "ws://localhost:" + port + "/ws/task-events",
                        new WebSocketHttpHeaders(),
                        connectHeaders,
                        new StompSessionHandlerAdapter() {
                        })
                .get(5, TimeUnit.SECONDS);
        CountDownLatch received = new CountDownLatch(1);
        session.subscribe("/user/queue/task-events", new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return byte[].class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                received.countDown();
            }
        });

        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (!hasTaskSubscription("demo") && System.nanoTime() < deadline) {
            Thread.sleep(20);
        }
        assertThat(hasTaskSubscription("demo")).isTrue();
        messagingTemplate.convertAndSendToUser(
                "demo",
                TaskEventBroadcaster.USER_QUEUE_DESTINATION,
                new TaskRealtimeEnvelope(
                        2,
                        TaskEventType.TASK_EVENT,
                        "task-123",
                        TaskLifecycleEvent.UPDATE,
                        null));

        assertThat(received.await(5, TimeUnit.SECONDS)).isTrue();
        session.disconnect();
        client.stop();
    }

    private boolean hasTaskSubscription(String username) {
        var user = userRegistry.getUser(username);
        return user != null && user.getSessions().stream()
                .flatMap(session -> session.getSubscriptions().stream())
                .anyMatch(subscription -> "/user/queue/task-events".equals(subscription.getDestination()));
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration(exclude = {
            CamundaBpmAutoConfiguration.class,
            CamundaWebSocketTaskEventsAutoConfiguration.class,
            CamundaRestAuthenticationTaskEventsAutoConfiguration.class,
            SpringOpaqueTokenTaskEventsAutoConfiguration.class
    })
    @EnableConfigurationProperties(RealtimeProperties.class)
    @Import({
            TaskRealtimeHandshakeHandler.class,
            TaskRealtimeMetrics.class,
            TaskRealtimeProtocolInterceptor.class,
            TaskRealtimeWebSocketConfig.class,
            TaskSessionLimitsInterceptor.class,
            TaskSessionRegistry.class,
            TaskWebSocketSessionTracker.class
    })
    static class TestApplication {
        @Bean
        JwtDecoder jwtDecoder() {
            return token -> {
                if (!"e2e-token".equals(token)) {
                    throw new IllegalArgumentException("invalid test token");
                }
                return Jwt.withTokenValue(token)
                        .header("alg", "RS256")
                        .subject("technical-subject")
                        .claim("preferred_username", "demo")
                        .issuedAt(Instant.now())
                        .expiresAt(Instant.now().plusSeconds(60))
                        .build();
            };
        }

        @Bean
        JwtAuthenticationConverter jwtAuthenticationConverter() {
            JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
            converter.setPrincipalClaimName("preferred_username");
            return converter;
        }

        @Bean
        SecurityFilterChain testSecurityFilterChain(HttpSecurity http) throws Exception {
            http.authorizeHttpRequests(authorize -> authorize.anyRequest().permitAll());
            return http.build();
        }
    }
}
