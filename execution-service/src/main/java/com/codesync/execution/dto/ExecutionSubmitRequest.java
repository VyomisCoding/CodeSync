package com.codesync.execution.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ExecutionSubmitRequest(
        @NotNull(message = "projectId is required")
        Long projectId,

        @NotNull(message = "fileId is required")
        Long fileId,

        @NotBlank(message = "language is required")
        @Size(max = 40, message = "language must be at most 40 characters")
        String language,

        @NotBlank(message = "sourceCode is required")
        String sourceCode,

        @Size(max = 20000, message = "stdin must be at most 20000 characters")
        String stdin
) {
}
