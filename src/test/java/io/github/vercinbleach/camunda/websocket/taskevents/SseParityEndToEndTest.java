package io.github.vercinbleach.camunda.websocket.taskevents;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.camunda.bpm.engine.ProcessEngine;
import org.camunda.bpm.engine.TaskService;
import org.camunda.bpm.engine.task.Task;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

@SpringBootTest(
        classes = SseParityEndToEndTest.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:sse-parity;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=false",
                "spring.datasource.username=sa",
                "spring.datasource.password=",
                "camunda.bpm.database.schema-update=true",
                "camunda.bpm.history-level=none",
                "camunda.bpm.job-execution.enabled=false",
                "camunda.bpm.authorization.enabled=true",
                "camunda.bpm.eventing.execution=false",
                "camunda.bpm.eventing.history=false",
                "camunda.bpm.eventing.skippable=false"
        })
class SseParityEndToEndTest {
    private static final String USERNAME = "demo";
    private static final String OUTSIDER = "outsider";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @LocalServerPort
    private int port;

    @jakarta.annotation.Resource
    private ProcessEngine processEngine;

    @jakarta.annotation.Resource
    private SimpUserRegistry userRegistry;

    @Test
    void publishesGranularCreateUpdateAssignmentCompleteAndDeleteEvents() throws Exception {
        BlockingQueue<JsonNode> messages = new LinkedBlockingQueue<>();
        BlockingQueue<JsonNode> outsiderMessages = new LinkedBlockingQueue<>();
        WebSocketStompClient client = new WebSocketStompClient(new StandardWebSocketClient());
        StompSession session = connect(client, "e2e-token", messages);
        StompSession outsiderSession = connect(client, "e2e-token-outsider", outsiderMessages);
        awaitSubscription(USERNAME);
        awaitSubscription(OUTSIDER);

        try {
            processEngine.getRepositoryService().createDeployment()
                    .addString("sse-parity.bpmn", process())
                    .deploy();
            var instance = processEngine.getRuntimeService().startProcessInstanceByKey("sse-parity");
            TaskService taskService = processEngine.getTaskService();
            Task task = taskService.createTaskQuery().processInstanceId(instance.getId()).singleResult();

            JsonNode create = awaitEvent(messages, task.getId(), "create");
            messages.clear();

            task.setDescription("updated by realtime parity test");
            taskService.saveTask(task);
            JsonNode update = awaitEvent(messages, task.getId(), "update");
            JsonNode updateInvalidation = awaitInvalidation(outsiderMessages);

            taskService.setAssignee(task.getId(), USERNAME);
            JsonNode assignment = awaitEvent(messages, task.getId(), "assignment");
            JsonNode assignmentInvalidation = awaitInvalidation(outsiderMessages);

            taskService.complete(task.getId());
            JsonNode complete = awaitEvent(messages, task.getId(), "complete");
            JsonNode outsiderCompleteInvalidation = awaitInvalidation(outsiderMessages);

            var deletedInstance = processEngine.getRuntimeService()
                    .startProcessInstanceByKey("sse-parity");
            Task deletedTask = taskService.createTaskQuery()
                    .processInstanceId(deletedInstance.getId())
                    .singleResult();
            awaitEvent(messages, deletedTask.getId(), "create");
            messages.clear();
            processEngine.getRuntimeService()
                    .deleteProcessInstance(deletedInstance.getId(), "realtime E2E");
            JsonNode deleteInvalidation = awaitInvalidation(messages);
            JsonNode outsiderDeleteInvalidation = awaitInvalidation(outsiderMessages);

            assertSoftly(softly -> {
                assertSseContext(softly, create, task.getId(), "create");
                assertSseContext(softly, update, task.getId(), "update");
                assertSseContext(softly, assignment, task.getId(), "assignment");
                assertSseContext(softly, complete, task.getId(), "complete");
                assertPayloadFreeInvalidation(softly, updateInvalidation);
                assertPayloadFreeInvalidation(softly, assignmentInvalidation);
                assertPayloadFreeInvalidation(softly, outsiderCompleteInvalidation);
                assertPayloadFreeInvalidation(softly, deleteInvalidation);
                assertPayloadFreeInvalidation(softly, outsiderDeleteInvalidation);
            });
        } finally {
            session.disconnect();
            outsiderSession.disconnect();
            client.stop();
        }
    }

    private StompSession connect(
            WebSocketStompClient client,
            String token,
            BlockingQueue<JsonNode> messages) throws Exception {
        StompHeaders connectHeaders = new StompHeaders();
        connectHeaders.set("Authorization", "Bearer " + token);
        StompSession session = client.connectAsync(
                        "ws://localhost:" + port + "/ws/task-events",
                        new WebSocketHttpHeaders(),
                        connectHeaders,
                        new StompSessionHandlerAdapter() {
                        })
                .get(5, TimeUnit.SECONDS);
        session.subscribe("/user/queue/task-events", new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return byte[].class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                try {
                    messages.add(OBJECT_MAPPER.readTree(new String((byte[]) payload, StandardCharsets.UTF_8)));
                } catch (Exception exception) {
                    throw new AssertionError("Invalid STOMP JSON payload", exception);
                }
            }
        });
        return session;
    }

    private void awaitSubscription(String username) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (!hasTaskSubscription(username) && System.nanoTime() < deadline) {
            Thread.sleep(20);
        }
        assertThat(hasTaskSubscription(username)).isTrue();
    }

    private boolean hasTaskSubscription(String username) {
        var user = userRegistry.getUser(username);
        return user != null && user.getSessions().stream()
                .flatMap(session -> session.getSubscriptions().stream())
                .anyMatch(subscription -> "/user/queue/task-events".equals(subscription.getDestination()));
    }

    private static JsonNode awaitEvent(BlockingQueue<JsonNode> messages, String taskId, String eventType)
            throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            JsonNode message = messages.poll(100, TimeUnit.MILLISECONDS);
            if (message != null
                    && taskId.equals(message.path("taskId").asText(null))
                    && eventType.equals(message.path("eventType").asText(null))) {
                return message;
            }
        }
        throw new AssertionError("No " + eventType + " STOMP event received for task " + taskId);
    }

    private static JsonNode awaitInvalidation(BlockingQueue<JsonNode> messages)
            throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            JsonNode message = messages.poll(100, TimeUnit.MILLISECONDS);
            if (message != null && "TASKS_INVALIDATED".equals(message.path("type").asText(null))) {
                return message;
            }
        }
        throw new AssertionError("No payload-free access invalidation received");
    }

    private static void assertSseContext(
            org.assertj.core.api.SoftAssertions softly,
            JsonNode message,
            String taskId,
            String eventType) {
        softly.assertThat(message.path("schemaVersion").asInt())
                .as("%s schema version", eventType)
                .isEqualTo(3);
        softly.assertThat(message.path("type").asText(null))
                .as("%s envelope type", eventType)
                .isEqualTo("complete".equals(eventType) || "delete".equals(eventType)
                        ? "TASK_REMOVE"
                        : "TASK_UPSERT");
        softly.assertThat(message.path("taskId").asText(null))
                .as("%s task id", eventType)
                .isEqualTo(taskId);
        softly.assertThat(message.path("eventType").asText(null))
                .as("%s event type", eventType)
                .isEqualTo(eventType);
        softly.assertThat(message.has("assignee")).as("%s assignee", eventType).isFalse();
    }

    private static void assertPayloadFreeInvalidation(
            org.assertj.core.api.SoftAssertions softly,
            JsonNode message) {
        softly.assertThat(message.path("schemaVersion").asInt()).isEqualTo(3);
        softly.assertThat(message.path("type").asText(null)).isEqualTo("TASKS_INVALIDATED");
        softly.assertThat(message.has("taskId")).isFalse();
        softly.assertThat(message.has("eventType")).isFalse();
        softly.assertThat(message.has("assignee")).isFalse();
    }

    private static String process() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<definitions xmlns=\"http://www.omg.org/spec/BPMN/20100524/MODEL\""
                + " xmlns:camunda=\"http://camunda.org/schema/1.0/bpmn\""
                + " targetNamespace=\"http://example.com/realtime\">"
                + "<process id=\"sse-parity\" isExecutable=\"true\" camunda:historyTimeToLive=\"1\">"
                + "<startEvent id=\"start\"/>"
                + "<userTask id=\"task\" camunda:candidateUsers=\"" + USERNAME + "\"/>"
                + "<endEvent id=\"end\"/>"
                + "<sequenceFlow id=\"start-task\" sourceRef=\"start\" targetRef=\"task\"/>"
                + "<sequenceFlow id=\"task-end\" sourceRef=\"task\" targetRef=\"end\"/>"
                + "</process></definitions>";
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class TestApplication {
        @Bean
        DataSource dataSource() {
            return new EmbeddedDatabaseBuilder()
                    .setType(EmbeddedDatabaseType.H2)
                    .setName("sse-parity")
                    .build();
        }

        @Bean
        JwtDecoder jwtDecoder() {
            return token -> {
                if (!"e2e-token".equals(token) && !"e2e-token-outsider".equals(token)) {
                    throw new IllegalArgumentException("invalid test token");
                }
                String username = "e2e-token".equals(token) ? USERNAME : OUTSIDER;
                return Jwt.withTokenValue(token)
                        .header("alg", "RS256")
                        .subject("technical-subject")
                        .claim("preferred_username", username)
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

        @Bean
        PlatformTransactionManager transactionManager(DataSource dataSource) {
            return new DataSourceTransactionManager(dataSource);
        }

    }
}
