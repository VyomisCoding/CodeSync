package com.codesync.version.dto;

public record SnapshotDiffResponse(
        Long leftSnapshotId,
        Long rightSnapshotId,
        String diff
) {
}
