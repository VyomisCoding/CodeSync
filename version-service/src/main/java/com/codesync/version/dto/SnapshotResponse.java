package com.codesync.version.dto;

import com.codesync.version.entity.Snapshot;

import java.time.LocalDateTime;

public record SnapshotResponse(
        Long snapshotId,
        Long projectId,
        Long fileId,
        Long authorId,
        String message,
        String content,
        String hash,
        Long parentSnapshotId,
        String branch,
        String tag,
        LocalDateTime createdAt
) {

    public static SnapshotResponse fromEntity(Snapshot snapshot) {
        return new SnapshotResponse(
                snapshot.getSnapshotId(),
                snapshot.getProjectId(),
                snapshot.getFileId(),
                snapshot.getAuthorId(),
                snapshot.getMessage(),
                snapshot.getContent(),
                snapshot.getHash(),
                snapshot.getParentSnapshotId(),
                snapshot.getBranch(),
                snapshot.getTag(),
                snapshot.getCreatedAt()
        );
    }
}
