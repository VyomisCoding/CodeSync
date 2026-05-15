package com.codesync.version.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateSnapshotRequest(
        @NotNull(message = "Project ID is required")
        Long projectId,
        @NotNull(message = "File ID is required")
        Long fileId,
        @NotBlank(message = "Message is required")
        @Size(max = 255, message = "Message must be at most 255 characters")
        String message,
        String content,
        @Size(max = 100, message = "Branch must be at most 100 characters")
        String branch
) {
}
