package com.codesync.execution.service;

import com.codesync.execution.dto.ExecutionStreamMessage;
import com.codesync.execution.entity.ExecutionStatus;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.UUID;

@Component
public class ExecutionEventPublisher {

    private final SimpMessagingTemplate messagingTemplate;

    public ExecutionEventPublisher(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    public void publishChunk(UUID jobId, String stream, ExecutionStatus status, String message) {
        messagingTemplate.convertAndSend(
                topic(jobId),
                new ExecutionStreamMessage(
                        jobId,
                        stream,
                        status,
                        message,
                        null,
                        null,
                        null,
                        LocalDateTime.now()
                )
        );
    }

    public void publishStatus(
            UUID jobId,
            ExecutionStatus status,
            String message,
            Integer exitCode,
            Long executionTimeMs,
            Long memoryUsedKb
    ) {
        messagingTemplate.convertAndSend(
                topic(jobId),
                new ExecutionStreamMessage(
                        jobId,
                        "STATUS",
                        status,
                        message,
                        exitCode,
                        executionTimeMs,
                        memoryUsedKb,
                        LocalDateTime.now()
                )
        );
    }

    private String topic(UUID jobId) {
        return "/topic/execution/" + jobId;
    }
}
