package io.github.vercinbleach.camunda.websocket.taskevents;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

@Configuration(proxyBeanMethods = false)
public class TaskRealtimePublisherConfiguration {
    static final int PUBLISHER_QUEUE_CAPACITY = 1024;

    @Bean(name = "taskRealtimePublisherExecutor")
    public ThreadPoolTaskExecutor taskRealtimePublisherExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(PUBLISHER_QUEUE_CAPACITY);
        executor.setThreadNamePrefix("task-realtime-publisher-");
        executor.setDaemon(true);
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.initialize();
        return executor;
    }
}
