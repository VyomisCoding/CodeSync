package com.codesync.version.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateBranchRequest(
        @NotNull(message = "Snapshot ID is required")
        Long snapshotId,
        @NotBlank(message = "Branch name is required")
        @Size(max = 100, message = "Branch name must be at most 100 characters")
        String branch,
        @Size(max = 255, message = "Message must be at most 255 characters")
        String message
) {
}
