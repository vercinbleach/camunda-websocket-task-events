package io.github.vercinbleach.camunda.websocket.taskevents;

import org.camunda.bpm.engine.IdentityService;
import org.camunda.bpm.engine.ProcessEngine;
import org.camunda.bpm.engine.ProcessEngineConfiguration;
import org.camunda.bpm.engine.TaskService;
import org.camunda.bpm.engine.identity.Group;
import org.camunda.bpm.engine.identity.GroupQuery;
import org.camunda.bpm.engine.identity.Tenant;
import org.camunda.bpm.engine.identity.TenantQuery;
import org.camunda.bpm.engine.impl.identity.Authentication;
import org.camunda.bpm.engine.task.TaskQuery;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.user.SimpUser;
import org.springframework.messaging.simp.user.SimpUserRegistry;

import java.security.Principal;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TaskEventBroadcasterTest {
    @Test
    void updatesInvalidateUsersWhoseCurrentMembershipCannotBeConfirmed() {
        TestHarness harness = new TestHarness();
        SimpUser visible = harness.user("visible", () -> "visible");
        SimpUser former = harness.user("former", () -> "former");
        when(harness.userRegistry.getUsers()).thenReturn(new LinkedHashSet<>(List.of(visible, former)));
        harness.visibilityCounts(1L, 0L, 0L);
        TaskRealtimeEnvelope envelope = taskEvent(TaskLifecycleEvent.UPDATE);

        harness.broadcaster().publish(envelope);

        verify(harness.template).convertAndSendToUser("visible", "/queue/task-events", envelope);
        verify(harness.template).convertAndSendToUser(
                "former", "/queue/task-events", TaskRealtimeEnvelope.accessInvalidated());
    }

    @Test
    void createDoesNotWakeUsersWhoCannotReadTheNewTask() {
        TestHarness harness = new TestHarness();
        SimpUser visible = harness.user("visible", () -> "visible");
        SimpUser unrelated = harness.user("unrelated", () -> "unrelated");
        when(harness.userRegistry.getUsers()).thenReturn(new LinkedHashSet<>(List.of(visible, unrelated)));
        harness.visibilityCounts(1L, 0L, 0L);

        harness.broadcaster().publish(taskEvent(TaskLifecycleEvent.CREATE));

        verify(harness.template).convertAndSendToUser(
                "visible", "/queue/task-events", taskEvent(TaskLifecycleEvent.CREATE));
        verify(harness.template, never()).convertAndSendToUser(
                eq("unrelated"), eq("/queue/task-events"), any());
    }

    @Test
    void honorsGlobalReadAuthorizationWithoutRequiringTaskMembership() {
        TestHarness harness = new TestHarness(true);
        SimpUser reader = harness.user("global-reader", () -> "global-reader");
        when(harness.userRegistry.getUsers()).thenReturn(new LinkedHashSet<>(List.of(reader)));
        harness.visibilityCounts(1L);
        TaskRealtimeEnvelope envelope = taskEvent(TaskLifecycleEvent.CREATE);

        harness.broadcaster().publish(envelope);

        verify(harness.template).convertAndSendToUser("global-reader", "/queue/task-events", envelope);
        verify(harness.taskQuery, never()).taskAssignee(anyString());
        verify(harness.taskQuery, never()).taskCandidateUser(anyString());
    }

    @Test
    void includesAnAssignedTaskForItsDirectCandidate() {
        TestHarness harness = new TestHarness();
        SimpUser candidate = harness.user("candidate", () -> "candidate");
        when(harness.userRegistry.getUsers()).thenReturn(new LinkedHashSet<>(List.of(candidate)));
        harness.visibilityCounts(0L, 1L);
        TaskRealtimeEnvelope envelope = taskEvent(TaskLifecycleEvent.ASSIGNMENT);

        harness.broadcaster().publish(envelope);

        verify(harness.taskQuery).taskCandidateUser("candidate");
        verify(harness.taskQuery).includeAssignedTasks();
        verify(harness.template).convertAndSendToUser("candidate", "/queue/task-events", envelope);
    }

    @Test
    void includesAnAssignedTaskForItsCandidateGroup() {
        TestHarness harness = new TestHarness();
        SimpUser candidate = harness.user("candidate", () -> "candidate");
        Group group = mock(Group.class);
        when(group.getId()).thenReturn("candidate-group");
        when(harness.groupQuery.list()).thenReturn(List.of(group));
        when(harness.userRegistry.getUsers()).thenReturn(new LinkedHashSet<>(List.of(candidate)));
        harness.visibilityCounts(0L, 0L, 1L);

        harness.broadcaster().publish(taskEvent(TaskLifecycleEvent.UPDATE));

        verify(harness.taskQuery).taskCandidateGroupIn(List.of("candidate-group"));
        verify(harness.taskQuery, atLeastOnce()).includeAssignedTasks();
    }

    @Test
    void sendsTaskRemovalOnlyToTheAssigneeCapturedBeforeCommit() {
        TestHarness harness = new TestHarness();
        SimpUser assignee = harness.user("assignee", () -> "assignee");
        SimpUser unknownRecipient = harness.user("unknown", () -> "unknown");
        when(harness.userRegistry.getUsers()).thenReturn(new LinkedHashSet<>(List.of(assignee, unknownRecipient)));
        TaskRealtimeEnvelope remove = taskEvent(TaskLifecycleEvent.COMPLETE);
        TaskRealtimePublication publication = new TaskRealtimePublication(remove, Set.of(), "assignee");

        harness.broadcaster().publish(harness.broadcaster().finalizePublicationAfterCommit(publication));

        verify(harness.template).convertAndSendToUser("assignee", "/queue/task-events", remove);
        verify(harness.template).convertAndSendToUser(
                "unknown", "/queue/task-events", TaskRealtimeEnvelope.accessInvalidated());
        verify(harness.taskService, never()).createTaskQuery();
    }

    @Test
    void capturesTerminalAssigneeOnlyWithEffectiveTenantAwareTaskRead() {
        TestHarness harness = new TestHarness(true);
        Tenant tenant = mock(Tenant.class);
        when(tenant.getId()).thenReturn("tenant-a");
        when(harness.tenantQuery.list()).thenReturn(List.of(tenant));
        harness.visibilityCounts(1L);

        TaskRealtimePublication publication = harness.broadcaster().capturePublication(
                taskEvent(TaskLifecycleEvent.COMPLETE),
                "assignee");

        assertThat(publication.capturedRemoveAssignee()).isEqualTo("assignee");
        verify(harness.identityService).setAuthentication("assignee", List.of(), List.of("tenant-a"));
    }

    @Test
    void removesTerminalMetadataWhenDirectReadAuthorizationCannotBeConfirmed() {
        TestHarness harness = new TestHarness(true);
        SimpUser assignee = harness.user("assignee", () -> "assignee");
        when(harness.userRegistry.getUsers()).thenReturn(Set.of(assignee));
        harness.visibilityCounts(0L);

        TaskRealtimePublication publication = harness.broadcaster().capturePublication(
                taskEvent(TaskLifecycleEvent.DELETE),
                "assignee");
        harness.broadcaster().publish(publication);

        assertThat(publication.capturedRemoveAssignee()).isNull();
        verify(harness.template).convertAndSendToUser(
                "assignee", "/queue/task-events", TaskRealtimeEnvelope.accessInvalidated());
    }

    @Test
    void restoresTheCommandsOriginalCamundaAuthentication() {
        TestHarness harness = new TestHarness();
        Authentication original = mock(Authentication.class);
        SimpUser member = harness.user("member", () -> "member");
        when(harness.identityService.getCurrentAuthentication()).thenReturn(original);
        when(harness.userRegistry.getUsers()).thenReturn(new LinkedHashSet<>(List.of(member)));
        harness.visibilityCounts(1L);

        harness.broadcaster().publish(taskEvent(TaskLifecycleEvent.UPDATE));

        var order = inOrder(harness.identityService);
        order.verify(harness.identityService).getCurrentAuthentication();
        order.verify(harness.identityService).clearAuthentication();
        order.verify(harness.identityService).createGroupQuery();
        order.verify(harness.identityService).createTenantQuery();
        order.verify(harness.identityService).setAuthentication("member", List.of(), List.of());
        order.verify(harness.identityService).setAuthentication(original);
    }

    @Test
    void skipsRoutingOnlyPrincipals() {
        TestHarness harness = new TestHarness();
        String userId = "task-events-session:legitimate-user";
        SimpUser legitimate = harness.user(userId, () -> userId);
        SimpUser routingOnly = harness.user(
                "task-events-session:opaque",
                new TaskRealtimeHandshakeHandler.RoutingOnlyPrincipal("task-events-session:opaque"));
        when(harness.userRegistry.getUsers()).thenReturn(new LinkedHashSet<>(List.of(legitimate, routingOnly)));
        harness.visibilityCounts(1L);

        harness.broadcaster().publish(taskEvent(TaskLifecycleEvent.CREATE));

        verify(harness.template).convertAndSendToUser(userId, "/queue/task-events", taskEvent(TaskLifecycleEvent.CREATE));
        verify(harness.template, never()).convertAndSendToUser(
                eq("task-events-session:opaque"), eq("/queue/task-events"), any());
    }

    private static TaskRealtimeEnvelope taskEvent(TaskLifecycleEvent eventType) {
        return new TaskRealtimeEnvelope(
                TaskRealtimeEnvelope.CURRENT_SCHEMA_VERSION,
                eventType.isUpsert() ? TaskEventType.TASK_UPSERT : TaskEventType.TASK_REMOVE,
                "task-123",
                eventType);
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
            when(taskQuery.taskAssignee(anyString())).thenReturn(taskQuery);
            when(taskQuery.taskCandidateUser(anyString())).thenReturn(taskQuery);
            when(taskQuery.taskCandidateGroupIn(anyList())).thenReturn(taskQuery);
            when(taskQuery.includeAssignedTasks()).thenReturn(taskQuery);
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
    }
}
