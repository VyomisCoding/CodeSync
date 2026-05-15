package com.codesync.comment.client;

public record FileSummaryResponse(
        Long fileId,
        Long projectId,
        String name,
        String path,
        String type,
        Boolean isDeleted
) {
}
