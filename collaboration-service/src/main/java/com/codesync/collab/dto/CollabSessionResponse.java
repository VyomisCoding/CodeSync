package com.codesync.collab.dto;

import com.codesync.collab.entity.SessionStatus;

import java.time.LocalDateTime;
import java.util.UUID;

public record CollabSessionResponse(
        UUID sessionId,
        Long projectId,
        Long fileId,
        Long ownerId,
        SessionStatus status,
        String language,
        LocalDateTime createdAt,
        LocalDateTime endedAt,
        Integer maxParticipants,
        boolean isPasswordProtected,
        long participantCount
) {
}
