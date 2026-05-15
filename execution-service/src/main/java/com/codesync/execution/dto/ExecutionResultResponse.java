package com.codesync.execution.dto;

import com.codesync.execution.entity.ExecutionStatus;

import java.time.LocalDateTime;
import java.util.UUID;

public record ExecutionResultResponse(
        UUID jobId,
        ExecutionStatus status,
        String stdout,
        String stderr,
        Integer exitCode,
        Long executionTimeMs,
        Long memoryUsedKb,
        LocalDateTime completedAt
) {
}
