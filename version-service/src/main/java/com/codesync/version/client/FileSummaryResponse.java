package com.codesync.version.client;

public record FileSummaryResponse(
        Long fileId,
        Long projectId,
        String name,
        String path,
        String type
) {
}
