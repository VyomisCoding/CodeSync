package com.codesync.execution.dto;

public record ExecutionStatsResponse(
        long totalExecutions,
        long queued,
        long running,
        long completed,
        long failed,
        long timedOut,
        long cancelled
) {
}
