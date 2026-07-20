package io.github.vercinbleach.camunda.websocket.taskevents;

import org.camunda.bpm.engine.IdentityService;
import org.camunda.bpm.engine.ProcessEngine;
import org.camunda.bpm.engine.ProcessEngineConfiguration;
import org.camunda.bpm.engine.TaskService;
import org.camunda.bpm.engine.identity.Group;
import org.camunda.bpm.engine.identity.GroupQuery;
import org.camunda.bpm.engine.identity.Tenant;
import org.camunda.bpm.engine.identity.TenantQuery;
import org.camunda.bpm.engine.task.TaskQuery;
import org.camunda.bpm.engine.task.Task;
import org.camunda.bpm.engine.impl.identity.Authentication;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.user.SimpUser;
import org.springframework.messaging.simp.user.SimpUserRegistry;

import java.security.Principal;
import java.util.LinkedHashSet;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.inOrder;
import static org.assertj.core.api.Assertions.assertThat;

class TaskEventBroadcasterTest {
    @Test
    void sendsTaskMetadataOnlyWhenCamundaConfirmsEffectiveVisibility() {
        TestHarness harness = new TestHarness();
        SimpUser visible = harness.user("visible", () -> "visible");
        SimpUser denied = harness.user("denied", () -> "denied");
        when(harness.userRegistry.getUsers()).thenReturn(new LinkedHashSet<>(List.of(visible, denied)));
        harness.visibilityCounts(1L, 0L, 1L);
        TaskRealtimeEnvelope envelope = taskEvent(TaskLifecycleEvent.CREATE, null);

        harness.broadcaster().publish(envelope);

        verify(harness.template).convertAndSendToUser("visible", "/queue/task-events", envelope);
        verify(harness.template, never())
                .convertAndSendToUser(eq("denied"), eq("/queue/task-events"), any());
    }

    @Test
    void honorsGlobalReadAuthorizationWithoutRequiringTaskMembership() {
        TestHarness harness = new TestHarness(true);
        SimpUser reader = harness.user("global-reader", () -> "global-reader");
        when(harness.userRegistry.getUsers()).thenReturn(new LinkedHashSet<>(List.of(reader)));
        harness.visibilityCounts(1L);
        TaskRealtimeEnvelope envelope = taskEvent(TaskLifecycleEvent.CREATE, null);

        harness.broadcaster().publish(envelope);

        verify(harness.template).convertAndSendToUser("global-reader", "/queue/task-events", envelope);
        verify(harness.taskQuery, never()).taskAssignee(anyString());
        verify(harness.taskQuery, never()).taskCandidateUser(anyString());
    }

    @Test
    void sendsPayloadFreeInvalidationWhenEffectiveVisibilityWasRevoked() {
        TestHarness harness = new TestHarness();
        SimpUser current = harness.user("current", () -> "current");
        SimpUser former = harness.user("former", () -> "former");
        when(harness.userRegistry.getUsers()).thenReturn(new LinkedHashSet<>(List.of(current, former)));
        harness.visibilityCounts(1L, 0L, 1L);
        harness.committedAssignee("current");
        TaskRealtimeEnvelope envelope = taskEvent(TaskLifecycleEvent.ASSIGNMENT, "current");

        harness.broadcaster().publish(envelope);

        verify(harness.template).convertAndSendToUser("current", "/queue/task-events", envelope);
        verify(harness.template).convertAndSendToUser(
                "former",
                "/queue/task-events",
                TaskRealtimeEnvelope.accessInvalidated());
    }

    @Test
    void emitsOnlyPayloadFreeInvalidationForTerminalEvents() {
        TestHarness harness = new TestHarness();
        SimpUser visible = harness.user("visible", () -> "visible");
        SimpUser outsider = harness.user("outsider", () -> "outsider");
        when(harness.userRegistry.getUsers()).thenReturn(new LinkedHashSet<>(List.of(visible, outsider)));
        TaskRealtimeEnvelope complete = taskEvent(TaskLifecycleEvent.COMPLETE, null);

        TaskRealtimePublication publication = harness.broadcaster().capturePublication(complete);
        publication = harness.broadcaster().finalizePublicationAfterCommit(publication);
        harness.broadcaster().publish(publication);

        assertThat(publication.visibleRecipientsBeforeCommit()).isEmpty();
        verify(harness.template).convertAndSendToUser(
                "visible", "/queue/task-events", TaskRealtimeEnvelope.accessInvalidated());
        verify(harness.template).convertAndSendToUser(
                "outsider", "/queue/task-events", TaskRealtimeEnvelope.accessInvalidated());
    }

    @Test
    void routesANonTerminalEventUsingItsCapturedVisibilitySnapshot() {
        TestHarness harness = new TestHarness();
        SimpUser alice = harness.user("alice", () -> "alice");
        SimpUser bob = harness.user("bob", () -> "bob");
        when(harness.userRegistry.getUsers()).thenReturn(new LinkedHashSet<>(List.of(alice, bob)));
        harness.visibilityCounts(1L, 0L);
        harness.committedAssignee("alice");
        TaskRealtimeEnvelope assignment = taskEvent(TaskLifecycleEvent.ASSIGNMENT, "alice");

        TaskRealtimePublication publication = harness.broadcaster().capturePublication(assignment);
        publication = harness.broadcaster().finalizePublicationAfterCommit(publication);
        clearInvocations(harness.taskQuery);
        harness.broadcaster().publish(publication);

        verify(harness.taskQuery, never()).count();
        verify(harness.template).convertAndSendToUser("alice", "/queue/task-events", assignment);
        verify(harness.template).convertAndSendToUser(
                "bob", "/queue/task-events", TaskRealtimeEnvelope.accessInvalidated());
    }

    @Test
    void dropsAStaleAssignmentWhoseAssigneeChangedBeforeCommit() {
        TestHarness harness = new TestHarness();
        SimpUser alice = harness.user("alice", () -> "alice");
        SimpUser bob = harness.user("bob", () -> "bob");
        when(harness.userRegistry.getUsers()).thenReturn(new LinkedHashSet<>(List.of(alice, bob)));
        harness.visibilityCounts(1L, 0L);
        harness.committedAssignee("bob");
        TaskRealtimeEnvelope assignment = taskEvent(TaskLifecycleEvent.ASSIGNMENT, "alice");

        TaskRealtimePublication publication = harness.broadcaster().capturePublication(assignment);
        publication = harness.broadcaster().finalizePublicationAfterCommit(publication);
        harness.broadcaster().publish(publication);

        assertThat(publication.visibleRecipientsBeforeCommit()).isEmpty();
        verify(harness.template).convertAndSendToUser(
                "alice", "/queue/task-events", TaskRealtimeEnvelope.accessInvalidated());
        verify(harness.template).convertAndSendToUser(
                "bob", "/queue/task-events", TaskRealtimeEnvelope.accessInvalidated());
    }

    @Test
    void restoresTheCommandsOriginalCamundaAuthentication() {
        TestHarness harness = new TestHarness();
        Authentication original = mock(Authentication.class);
        SimpUser member = harness.user("member", () -> "member");
        when(harness.identityService.getCurrentAuthentication()).thenReturn(original);
        when(harness.userRegistry.getUsers()).thenReturn(new LinkedHashSet<>(List.of(member)));
        harness.visibilityCounts(1L);

        TaskRealtimePublication publication = harness.broadcaster()
                .capturePublication(taskEvent(TaskLifecycleEvent.UPDATE, null));
        harness.broadcaster().finalizePublicationAfterCommit(publication);

        var order = inOrder(harness.identityService);
        order.verify(harness.identityService).getCurrentAuthentication();
        order.verify(harness.identityService).clearAuthentication();
        order.verify(harness.identityService).createGroupQuery();
        order.verify(harness.identityService).createTenantQuery();
        order.verify(harness.identityService).setAuthentication("member", List.of(), List.of());
        order.verify(harness.identityService).setAuthentication(original);
    }

    @Test
    void evaluatesCamundaVisibilityWithTheRecipientsGroupsAndTenants() {
        TestHarness harness = new TestHarness();
        SimpUser member = harness.user("member", () -> "member");
        Group group = mock(Group.class);
        Tenant tenant = mock(Tenant.class);
        when(group.getId()).thenReturn("candidate-group");
        when(tenant.getId()).thenReturn("tenant-a");
        when(harness.groupQuery.list()).thenReturn(List.of(group));
        when(harness.tenantQuery.list()).thenReturn(List.of(tenant));
        when(harness.userRegistry.getUsers()).thenReturn(new LinkedHashSet<>(List.of(member)));
        harness.visibilityCounts(1L);

        harness.broadcaster().publish(taskEvent(TaskLifecycleEvent.CREATE, null));

        verify(harness.identityService).setAuthentication(
                "member",
                List.of("candidate-group"),
                List.of("tenant-a"));
        verify(harness.identityService, atLeastOnce()).clearAuthentication();
    }

    @Test
    void distinguishesRoutingOnlyPrincipalsByTypeRatherThanUsernamePrefix() {
        TestHarness harness = new TestHarness();
        String prefixedName = "task-events-session:legitimate-user";
        SimpUser legitimate = harness.user(prefixedName, () -> prefixedName);
        SimpUser routingOnly = harness.user(
                "task-events-session:opaque",
                new TaskRealtimeHandshakeHandler.RoutingOnlyPrincipal("task-events-session:opaque"));
        when(harness.userRegistry.getUsers()).thenReturn(
                new LinkedHashSet<>(List.of(legitimate, routingOnly)));
        harness.visibilityCounts(1L);
        TaskRealtimeEnvelope envelope = taskEvent(TaskLifecycleEvent.CREATE, null);

        harness.broadcaster().publish(envelope);

        verify(harness.template).convertAndSendToUser(prefixedName, "/queue/task-events", envelope);
        verify(harness.template, never()).convertAndSendToUser(
                eq("task-events-session:opaque"),
                eq("/queue/task-events"),
                any());
    }

    private static TaskRealtimeEnvelope taskEvent(TaskLifecycleEvent eventType, String assignee) {
        return new TaskRealtimeEnvelope(
                2,
                TaskEventType.TASK_EVENT,
                "task-123",
                eventType,
                assignee);
    }

    private static final class TestHarness {
        private final SimpMessagingTemplate template = mock(SimpMessagingTemplate.class);
        private final SimpUserRegistry userRegistry = mock(SimpUserRegistry.class);
        private final IdentityService identityService = mock(IdentityService.class);
        private final TaskService taskService = mock(TaskService.class);
        private final ProcessEngine processEngine = mock(ProcessEngine.class);
        private final ProcessEngineConfiguration processEngineConfiguration = mock(ProcessEngineConfiguration.class);
        private final GroupQuery groupQuery = mock(GroupQuery.class);
        private final TenantQuery tenantQuery = mock(TenantQuery.class);
        private final TaskQuery taskQuery = mock(TaskQuery.class);
        private final Task committedTask = mock(Task.class);

        private TestHarness() {
            this(false);
        }

        private TestHarness(boolean authorizationEnabled) {
            when(processEngine.getProcessEngineConfiguration()).thenReturn(processEngineConfiguration);
            when(processEngineConfiguration.isAuthorizationEnabled()).thenReturn(authorizationEnabled);
            when(identityService.createGroupQuery()).thenReturn(groupQuery);
            when(groupQuery.groupMember(anyString())).thenReturn(groupQuery);
            when(groupQuery.list()).thenReturn(List.of());
            when(identityService.createTenantQuery()).thenReturn(tenantQuery);
            when(tenantQuery.userMember(anyString())).thenReturn(tenantQuery);
            when(tenantQuery.includingGroupsOfUser(true)).thenReturn(tenantQuery);
            when(tenantQuery.list()).thenReturn(List.of());
            when(taskService.createTaskQuery()).thenReturn(taskQuery);
            when(taskQuery.taskId(anyString())).thenReturn(taskQuery);
            when(taskQuery.or()).thenReturn(taskQuery);
            when(taskQuery.taskAssignee(anyString())).thenReturn(taskQuery);
            when(taskQuery.taskCandidateUser(anyString())).thenReturn(taskQuery);
            when(taskQuery.endOr()).thenReturn(taskQuery);
        }

        private TaskEventBroadcaster broadcaster() {
            return new TaskEventBroadcaster(
                    template,
                    userRegistry,
                    identityService,
                    taskService,
                    processEngine,
                    new TaskRealtimeMetrics());
        }

        private SimpUser user(String name, Principal principal) {
            SimpUser user = mock(SimpUser.class);
            when(user.getName()).thenReturn(name);
            when(user.getPrincipal()).thenReturn(principal);
            return user;
        }

        private void visibilityCounts(Long... counts) {
            when(taskQuery.count()).thenReturn(counts[0], java.util.Arrays.copyOfRange(counts, 1, counts.length));
        }

        private void committedAssignee(String assignee) {
            when(taskQuery.singleResult()).thenReturn(committedTask);
            when(committedTask.getAssignee()).thenReturn(assignee);
        }
    }
}
