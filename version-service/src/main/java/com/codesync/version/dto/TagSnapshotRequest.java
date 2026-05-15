package com.codesync.version.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record TagSnapshotRequest(
        @NotNull(message = "Snapshot ID is required")
        Long snapshotId,
        @NotBlank(message = "Tag is required")
        @Size(max = 100, message = "Tag must be at most 100 characters")
        String tag
) {
}
