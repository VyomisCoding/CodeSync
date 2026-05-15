package com.codesync.collab.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.UUID;

public record CodeChangeMessage(
        UUID sessionId,
        Long fileId,
        Long userId,
        String language,

        @NotBlank(message = "content is required")
        @Size(max = 500000, message = "content is too large")
        String content,

        LocalDateTime changedAt
) {
}
