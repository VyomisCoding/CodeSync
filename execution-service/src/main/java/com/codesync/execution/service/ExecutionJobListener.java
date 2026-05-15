package com.codesync.execution.service;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@ConditionalOnProperty(name = "codesync.execution.rabbitmq.enabled", havingValue = "true")
public class ExecutionJobListener {

    private final ExecutionJobProcessor executionJobProcessor;

    public ExecutionJobListener(ExecutionJobProcessor executionJobProcessor) {
        this.executionJobProcessor = executionJobProcessor;
    }

    @RabbitListener(queues = "${codesync.execution.rabbitmq.queue-name:execution.jobs}")
    public void handleExecutionJob(String jobId) {
        executionJobProcessor.processQueuedJob(UUID.fromString(jobId));
    }
}
