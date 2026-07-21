package io.github.vercinbleach.camunda.websocket.taskevents;


import org.camunda.bpm.engine.ProcessEngine;
import org.camunda.bpm.engine.RepositoryService;
import org.camunda.bpm.engine.RuntimeService;
import org.camunda.bpm.engine.TaskService;
import org.camunda.bpm.engine.delegate.JavaDelegate;
import org.camunda.bpm.engine.repository.Deployment;
import org.camunda.bpm.engine.runtime.ProcessInstance;
import org.camunda.bpm.engine.task.Task;
import org.camunda.bpm.engine.spring.SpringProcessEngineConfiguration;
import org.camunda.bpm.spring.boot.starter.event.EventPublisherPlugin;
import org.camunda.bpm.spring.boot.starter.property.EventingProperty;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.ArgumentMatchers.any;

@SpringJUnitConfig(CamundaTaskEventListenerTest.TestConfiguration.class)
class CamundaTaskEventListenerTest {

    @jakarta.annotation.Resource
    private ProcessEngine processEngine;

    @jakarta.annotation.Resource
    private TaskEventPublicationService publicationService;

    @Test
    void publishesAfterCommitWhenProcessDeletionSkipsCustomListeners() {
        RuntimeService runtimeService = processEngine.getRuntimeService();
        ProcessInstance instance = startProcess("realtime-skip-listeners");
        reset(publicationService);

        runtimeService.deleteProcessInstance(instance.getId(), "test", true, false);

        verify(publicationService).enqueue(any(TaskRealtimePublication.class));
        assertThat(runtimeService.createProcessInstanceQuery()
                .processInstanceId(instance.getId())
                .count()).isZero();
    }

    @Test
    void doesNotPublishAfterRollbackOfCommandThatEmittedTaskEvent() {
        RuntimeService runtimeService = processEngine.getRuntimeService();
        TaskService taskService = processEngine.getTaskService();
        ProcessInstance instance = startProcess("realtime-rollback");
        Task task = taskService.createTaskQuery()
                .processInstanceId(instance.getId())
                .singleResult();
        reset(publicationService);

        assertThatThrownBy(() -> taskService.complete(task.getId()))
                .hasMessage("intentional realtime rollback");

        verifyNoInteractions(publicationService);
        assertThat(taskService.createTaskQuery().taskId(task.getId()).count()).isOne();
    }

    private ProcessInstance startProcess(String processDefinitionKey) {
        RepositoryService repositoryService = processEngine.getRepositoryService();
        Deployment deployment = repositoryService.createDeployment()
                .addString(processDefinitionKey + ".bpmn", bpmn(processDefinitionKey))
                .deploy();

        ProcessInstance instance = processEngine.getRuntimeService()
                .startProcessInstanceByKey(processDefinitionKey);
        verify(publicationService, atLeastOnce()).enqueue(any(TaskRealtimePublication.class));
        assertThat(repositoryService.createProcessDefinitionQuery()
                .deploymentId(deployment.getId())
                .count()).isOne();
        return instance;
    }

    private static String bpmn(String processDefinitionKey) {
        String serviceTask = "realtime-rollback".equals(processDefinitionKey)
                ? "<serviceTask id=\"fail\" camunda:delegateExpression=\"${failingDelegate}\"/>"
                + "<endEvent id=\"end\"/>"
                : "<endEvent id=\"end\"/>";
        String finalFlow = "realtime-rollback".equals(processDefinitionKey)
                ? "<sequenceFlow id=\"flow-task-fail\" sourceRef=\"task\" targetRef=\"fail\"/>"
                + "<sequenceFlow id=\"flow-fail-end\" sourceRef=\"fail\" targetRef=\"end\"/>"
                : "<sequenceFlow id=\"flow-task-end\" sourceRef=\"task\" targetRef=\"end\"/>";
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<definitions xmlns=\"http://www.omg.org/spec/BPMN/20100524/MODEL\""
                + " xmlns:camunda=\"http://camunda.org/schema/1.0/bpmn\""
                + " targetNamespace=\"http://example.com/realtime\">"
                + "<process id=\"" + processDefinitionKey + "\" isExecutable=\"true\">"
                + "<startEvent id=\"start\"/>"
                + "<userTask id=\"task\"/>"
                + serviceTask
                + finalFlow
                + "<sequenceFlow id=\"flow-start-task\" sourceRef=\"start\" targetRef=\"task\"/>"
                + "</process></definitions>";
    }

    @Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement
    @Import(CamundaTaskEventListener.class)
    static class TestConfiguration {
        @Bean
        DataSource dataSource() {
            return new EmbeddedDatabaseBuilder()
                    .setType(EmbeddedDatabaseType.H2)
                    .setName("camunda-realtime-test")
                    .build();
        }

        @Bean
        PlatformTransactionManager transactionManager(DataSource dataSource) {
            return new DataSourceTransactionManager(dataSource);
        }

        @Bean
        EventingProperty eventingProperty() {
            EventingProperty property = new EventingProperty();
            property.setTask(true);
            property.setExecution(false);
            property.setHistory(false);
            property.setSkippable(false);
            return property;
        }

        @Bean
        EventPublisherPlugin eventPublisherPlugin(
                EventingProperty eventingProperty,
                ApplicationEventPublisher applicationEventPublisher) {
            return new EventPublisherPlugin(eventingProperty, applicationEventPublisher);
        }

        @Bean
        JavaDelegate failingDelegate() {
            return execution -> {
                throw new IllegalStateException("intentional realtime rollback");
            };
        }

        @Bean(destroyMethod = "close")
        ProcessEngine processEngine(
                DataSource dataSource,
                PlatformTransactionManager transactionManager,
                EventPublisherPlugin eventPublisherPlugin,
                JavaDelegate failingDelegate) {
            SpringProcessEngineConfiguration configuration = new SpringProcessEngineConfiguration();
            configuration.setDataSource(dataSource);
            configuration.setTransactionManager(transactionManager);
            configuration.setDatabaseSchemaUpdate("create-drop");
            configuration.setHistory("none");
            configuration.setEnforceHistoryTimeToLive(false);
            configuration.setJobExecutorActivate(false);
            configuration.setCustomPostBPMNParseListeners(new ArrayList<>());
            configuration.setProcessEnginePlugins(List.of(eventPublisherPlugin));
            configuration.setBeans(Map.of("failingDelegate", failingDelegate));
            return configuration.buildProcessEngine();
        }

        @Bean
        TaskEventPublicationService publicationService() {
            return mock(TaskEventPublicationService.class);
        }

        @Bean
        TaskService taskService(ProcessEngine processEngine) {
            return processEngine.getTaskService();
        }
    }
}
