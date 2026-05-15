package com.codesync.collab.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CollabSessionRequest(
        @NotNull(message = "projectId is required")
        Long projectId,

        @NotNull(message = "fileId is required")
        Long fileId,

        @NotBlank(message = "language is required")
        @Size(max = 80, message = "language must be at most 80 characters")
        String language,

        @NotNull(message = "maxParticipants is required")
        @Min(value = 1, message = "maxParticipants must be at least 1")
        Integer maxParticipants,

        @NotNull(message = "isPasswordProtected is required")
        Boolean isPasswordProtected,

        @Size(max = 100, message = "sessionPassword must be at most 100 characters")
        String sessionPassword
) {
}
