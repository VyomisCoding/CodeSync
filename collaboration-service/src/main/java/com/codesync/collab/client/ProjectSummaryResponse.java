package com.codesync.collab.client;

public record ProjectSummaryResponse(
        Long projectId,
        Long ownerId,
        String visibility,
        Boolean isArchived
) {
}
