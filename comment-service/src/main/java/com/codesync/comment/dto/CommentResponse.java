package com.codesync.comment.dto;

import com.codesync.comment.entity.Comment;

import java.time.LocalDateTime;

public record CommentResponse(
        Long commentId,
        Long projectId,
        Long fileId,
        Long authorId,
        String content,
        Integer lineNumber,
        Integer columnNumber,
        Long parentCommentId,
        Boolean resolved,
        Long snapshotId,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static CommentResponse fromEntity(Comment comment) {
        return new CommentResponse(
                comment.getCommentId(),
                comment.getProjectId(),
                comment.getFileId(),
                comment.getAuthorId(),
                comment.getContent(),
                comment.getLineNumber(),
                comment.getColumnNumber(),
                comment.getParentCommentId(),
                comment.getResolved(),
                comment.getSnapshotId(),
                comment.getCreatedAt(),
                comment.getUpdatedAt()
        );
    }
}
