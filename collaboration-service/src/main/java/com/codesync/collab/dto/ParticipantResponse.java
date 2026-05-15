package com.codesync.collab.dto;

import com.codesync.collab.entity.ParticipantRole;

import java.time.LocalDateTime;
import java.util.UUID;

public record ParticipantResponse(
        Long participantId,
        UUID sessionId,
        Long userId,
        ParticipantRole role,
        LocalDateTime joinedAt,
        LocalDateTime leftAt,
        Integer cursorLine,
        Integer cursorCol,
        String color
) {
}
