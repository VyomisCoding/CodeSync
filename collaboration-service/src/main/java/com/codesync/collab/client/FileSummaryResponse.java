package com.codesync.collab.client;

public record FileSummaryResponse(
        Long fileId,
        Long projectId,
        String name,
        String path,
        String type
) {
}
