package com.codesync.comment.client;

public record SnapshotSummaryResponse(
        Long snapshotId,
        Long projectId,
        Long fileId,
        Long authorId
) {
}
