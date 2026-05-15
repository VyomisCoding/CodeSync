package com.codesync.version.client;

public record ProjectSummaryResponse(
        Long projectId,
        Long ownerId,
        String visibility,
        Boolean isArchived
) {
}
