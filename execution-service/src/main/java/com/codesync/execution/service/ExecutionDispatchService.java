package com.codesync.execution.service;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.Executor;

@Service
public class ExecutionDispatchService {

    private final ObjectProvider<RabbitTemplate> rabbitTemplateProvider;
    private final Executor executionTaskExecutor;
    private final ExecutionJobProcessor executionJobProcessor;
    private final boolean rabbitEnabled;
    private final String queueName;

    public ExecutionDispatchService(
            ObjectProvider<RabbitTemplate> rabbitTemplateProvider,
            @Qualifier("executionTaskExecutor") Executor executionTaskExecutor,
            ExecutionJobProcessor executionJobProcessor,
            @Value("${codesync.execution.rabbitmq.enabled:false}") boolean rabbitEnabled,
            @Value("${codesync.execution.rabbitmq.queue-name:execution.jobs}") String queueName) {
        this.rabbitTemplateProvider = rabbitTemplateProvider;
        this.executionTaskExecutor = executionTaskExecutor;
        this.executionJobProcessor = executionJobProcessor;
        this.rabbitEnabled = rabbitEnabled;
        this.queueName = queueName;
    }

    public void dispatch(UUID jobId) {
        if (rabbitEnabled) {
            RabbitTemplate rabbitTemplate = rabbitTemplateProvider.getIfAvailable();
            if (rabbitTemplate != null) {
                try {
                    rabbitTemplate.convertAndSend(queueName, jobId.toString());
                    return;
                } catch (RuntimeException ignored) {
                }
            }
        }

        executionTaskExecutor.execute(() -> executionJobProcessor.processQueuedJob(jobId));
    }
}
