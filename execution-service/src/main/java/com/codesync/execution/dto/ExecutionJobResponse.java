package com.codesync.execution.dto;

import com.codesync.execution.entity.ExecutionStatus;

import java.time.LocalDateTime;
import java.util.UUID;

public record ExecutionJobResponse(
        UUID jobId,
        Long projectId,
        Long fileId,
        Long userId,
        String language,
        ExecutionStatus status,
        Integer exitCode,
        Long executionTimeMs,
        Long memoryUsedKb,
        LocalDateTime createdAt,
        LocalDateTime completedAt
) {
}
