package com.codesync.comment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record CreateCommentRequest(
        @NotNull(message = "projectId is required")
        Long projectId,
        @NotNull(message = "fileId is required")
        Long fileId,
        @NotBlank(message = "content is required")
        @Size(max = 5000, message = "content must be at most 5000 characters")
        String content,
        @Positive(message = "lineNumber must be positive")
        Integer lineNumber,
        @PositiveOrZero(message = "columnNumber must be zero or positive")
        Integer columnNumber,
        @Positive(message = "parentCommentId must be positive")
        Long parentCommentId,
        @Positive(message = "snapshotId must be positive")
        Long snapshotId
) {
}
