package com.codesync.execution.client;

public record ProjectSummaryResponse(
        Long projectId,
        Long ownerId,
        String visibility,
        Boolean isArchived
) {
}
