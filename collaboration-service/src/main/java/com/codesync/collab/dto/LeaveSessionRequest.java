package com.codesync.collab.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record LeaveSessionRequest(
        @NotNull(message = "sessionId is required")
        UUID sessionId
) {
}
