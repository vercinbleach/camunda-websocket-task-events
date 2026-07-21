package io.github.vercinbleach.camunda.websocket.taskevents;

import org.camunda.bpm.engine.IdentityService;
import org.camunda.bpm.engine.ProcessEngine;
import org.camunda.bpm.engine.TaskService;
import org.camunda.bpm.engine.task.TaskQuery;
import org.camunda.bpm.engine.impl.identity.Authentication;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.user.SimpUser;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.LinkedHashSet;
import java.util.List;

@Component
public class TaskEventBroadcaster {
    public static final String USER_QUEUE_DESTINATION = "/queue/task-events";

    private static final Logger logger = LoggerFactory.getLogger(TaskEventBroadcaster.class);

    private final SimpMessagingTemplate messagingTemplate;
    private final SimpUserRegistry userRegistry;
    private final IdentityService identityService;
    private final TaskService taskService;
    private final boolean authorizationEnabled;
    private final TaskRealtimeMetrics metrics;

    @Autowired
    public TaskEventBroadcaster(SimpMessagingTemplate messagingTemplate,
                                SimpUserRegistry userRegistry,
                                IdentityService identityService,
                                TaskService taskService,
                                ProcessEngine processEngine,
                                TaskRealtimeMetrics metrics) {
        this.messagingTemplate = messagingTemplate;
        this.userRegistry = userRegistry;
        this.identityService = identityService;
        this.taskService = taskService;
        this.authorizationEnabled = processEngine.getProcessEngineConfiguration()
                .isAuthorizationEnabled();
        this.metrics = metrics;
    }

    public TaskRealtimePublication capturePublication(TaskRealtimeEnvelope envelope) {
        return capturePublication(envelope, null);
    }

    public TaskRealtimePublication capturePublication(TaskRealtimeEnvelope envelope, String assignee) {
        String authorizedRemoveAssignee = envelope.isRemove()
                && assignee != null
                && !assignee.isBlank()
                && canReadTask(assignee, envelope)
                ? assignee
                : null;
        return TaskRealtimePublication.capture(envelope, authorizedRemoveAssignee);
    }

    public TaskRealtimePublication finalizePublicationAfterCommit(TaskRealtimePublication publication) {
        TaskRealtimeEnvelope envelope = publication.envelope();
        if (envelope.requiresPostCommitVisibilityCapture()) {
            return publication.withVisibleRecipients(captureVisibleRecipients(envelope));
        }
        return publication;
    }

    private Set<String> captureVisibleRecipients(TaskRealtimeEnvelope envelope) {
        Set<String> visibleRecipients = new LinkedHashSet<>();
        for (SimpUser user : connectedUsers()) {
            if (isEligibleRecipient(user) && canReadTask(user.getName(), envelope)) {
                visibleRecipients.add(user.getName());
            }
        }
        return visibleRecipients;
    }

    public void publish(TaskRealtimePublication publication) {
        TaskRealtimeEnvelope envelope = publication.envelope();
        Set<SimpUser> users = connectedUsers();
        if (users == null || users.isEmpty()) {
            return;
        }

        int attempted = 0;
        for (SimpUser user : users) {
            if (!isEligibleRecipient(user)) {
                continue;
            }
            TaskRealtimeEnvelope outboundEnvelope = outboundEnvelope(publication, user.getName());
            if (outboundEnvelope == null) {
                continue;
            }
            try {
                messagingTemplate.convertAndSendToUser(
                        user.getName(),
                        USER_QUEUE_DESTINATION,
                        outboundEnvelope);
                attempted++;
            } catch (RuntimeException exception) {
                metrics.recordDeliveryFailure();
                logger.debug("Realtime delivery failed without user or payload details");
            }
        }
        if (attempted > 0) {
            metrics.recordEnvelopeEmitted();
        }
        logger.debug("Realtime task event attempted recipients={}", attempted);
    }

    public void publish(TaskRealtimeEnvelope envelope) {
        TaskRealtimePublication publication = capturePublication(envelope);
        publish(finalizePublicationAfterCommit(publication));
    }

    private TaskRealtimeEnvelope outboundEnvelope(TaskRealtimePublication publication, String userId) {
        TaskRealtimeEnvelope envelope = publication.envelope();
        if (envelope.type() == TaskEventType.TASK_UPSERT) {
            if (publication.visibleRecipients().contains(userId)) {
                return envelope;
            }
            return envelope.eventType() == TaskLifecycleEvent.CREATE
                    ? null
                    : TaskRealtimeEnvelope.accessInvalidated();
        }
        if (envelope.type() == TaskEventType.TASK_REMOVE
                && userId.equals(publication.capturedRemoveAssignee())) {
            return envelope;
        }
        return TaskRealtimeEnvelope.accessInvalidated();
    }

    private Set<SimpUser> connectedUsers() {
        try {
            Set<SimpUser> users = userRegistry.getUsers();
            return users == null ? Set.of() : users;
        } catch (RuntimeException exception) {
            logger.warn("Realtime user registry unavailable without payload details");
            return Set.of();
        }
    }

    private boolean isEligibleRecipient(SimpUser user) {
        return user != null
                && user.getName() != null
                && !user.getName().isBlank()
                && !TaskRealtimeHandshakeHandler.isRoutingOnlyPrincipal(user.getPrincipal());
    }

    private boolean canReadTask(String userId, TaskRealtimeEnvelope envelope) {
        Authentication previousAuthentication = identityService.getCurrentAuthentication();
        try {
            identityService.clearAuthentication();
            List<String> groupIds = identityService.createGroupQuery()
                    .groupMember(userId)
                    .list()
                    .stream()
                    .map(group -> group.getId())
                    .filter(groupId -> groupId != null && !groupId.isBlank())
                    .toList();
            List<String> tenantIds = identityService.createTenantQuery()
                    .userMember(userId)
                    .includingGroupsOfUser(true)
                    .list()
                    .stream()
                    .map(tenant -> tenant.getId())
                    .toList();
            identityService.setAuthentication(userId, groupIds, tenantIds);
            if (authorizationEnabled) {
                return hasTask(taskService.createTaskQuery().taskId(envelope.taskId()));
            }
            return hasTask(taskService.createTaskQuery().taskId(envelope.taskId()).taskAssignee(userId))
                    || hasTask(taskService.createTaskQuery()
                    .taskId(envelope.taskId())
                    .taskCandidateUser(userId)
                    .includeAssignedTasks())
                    || (!groupIds.isEmpty() && hasTask(taskService.createTaskQuery()
                    .taskId(envelope.taskId())
                    .taskCandidateGroupIn(groupIds)
                    .includeAssignedTasks()));
        } catch (RuntimeException exception) {
            logger.warn("Could not verify realtime task visibility without user or payload details");
            return false;
        } finally {
            if (previousAuthentication == null) {
                identityService.clearAuthentication();
            } else {
                identityService.setAuthentication(previousAuthentication);
            }
        }
    }

    private boolean hasTask(TaskQuery query) {
        return query.count() > 0;
    }

}
