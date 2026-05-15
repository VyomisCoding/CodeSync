package com.codesync.notification.client;

public record AuthUserSummaryResponse(
        Long userId,
        String username,
        String fullName,
        String avatarUrl,
        String bio
) {
}
