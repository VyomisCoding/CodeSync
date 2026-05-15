package com.codesync.collab.dto;

import com.codesync.collab.entity.ParticipantRole;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record JoinSessionRequest(
        @NotNull(message = "sessionId is required")
        UUID sessionId,

        ParticipantRole role,

        @Size(max = 100, message = "sessionPassword must be at most 100 characters")
        String sessionPassword
) {
}
