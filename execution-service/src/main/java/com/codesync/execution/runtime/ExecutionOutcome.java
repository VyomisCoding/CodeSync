package com.codesync.execution.runtime;

import com.codesync.execution.entity.ExecutionStatus;

public record ExecutionOutcome(
        ExecutionStatus status,
        String stdout,
        String stderr,
        Integer exitCode,
        Long executionTimeMs,
        Long memoryUsedKb
) {
}
