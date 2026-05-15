package com.codesync.collab.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record KickParticipantRequest(
        @NotNull(message = "sessionId is required")
        UUID sessionId,

        @NotNull(message = "participantId is required")
        Long participantId
) {
}
