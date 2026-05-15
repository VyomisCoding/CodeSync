package com.codesync.execution.client;

public record FileSummaryResponse(
        Long fileId,
        Long projectId,
        String name,
        String path,
        String type
) {
}
