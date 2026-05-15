package com.codesync.execution.dto;

import com.codesync.execution.entity.ExecutionStatus;

import java.time.LocalDateTime;
import java.util.UUID;

public record ExecutionStreamMessage(
        UUID jobId,
        String stream,
        ExecutionStatus status,
        String message,
        Integer exitCode,
        Long executionTimeMs,
        Long memoryUsedKb,
        LocalDateTime emittedAt
) {
}
