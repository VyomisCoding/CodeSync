package com.codesync.comment.client;

public record ProjectSummaryResponse(
        Long projectId,
        Long ownerId,
        String name,
        String visibility
) {
}
