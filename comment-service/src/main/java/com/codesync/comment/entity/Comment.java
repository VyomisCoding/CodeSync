package com.codesync.comment.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "comments",
        indexes = {
                @Index(name = "idx_comments_project", columnList = "project_id"),
                @Index(name = "idx_comments_file", columnList = "file_id"),
                @Index(name = "idx_comments_author", columnList = "author_id"),
                @Index(name = "idx_comments_parent", columnList = "parent_comment_id"),
                @Index(name = "idx_comments_resolved", columnList = "resolved"),
                @Index(name = "idx_comments_file_line", columnList = "file_id,line_number")
        }
)
public class Comment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "comment_id")
    private Long commentId;

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Column(name = "file_id", nullable = false)
    private Long fileId;

    @Column(name = "author_id", nullable = false)
    private Long authorId;

    @Lob
    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "line_number")
    private Integer lineNumber;

    @Column(name = "column_number")
    private Integer columnNumber;

    @Column(name = "parent_comment_id")
    private Long parentCommentId;

    @Column(name = "resolved", nullable = false)
    private Boolean resolved;

    @Column(name = "snapshot_id")
    private Long snapshotId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public Comment() {
    }

    public Comment(
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
            LocalDateTime updatedAt) {
        this.commentId = commentId;
        this.projectId = projectId;
        this.fileId = fileId;
        this.authorId = authorId;
        this.content = content;
        this.lineNumber = lineNumber;
        this.columnNumber = columnNumber;
        this.parentCommentId = parentCommentId;
        this.resolved = resolved;
        this.snapshotId = snapshotId;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    @PrePersist
    public void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
        if (resolved == null) {
            resolved = false;
        }
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
        if (resolved == null) {
            resolved = false;
        }
    }

    public Long getCommentId() {
        return commentId;
    }

    public void setCommentId(Long commentId) {
        this.commentId = commentId;
    }

    public Long getProjectId() {
        return projectId;
    }

    public void setProjectId(Long projectId) {
        this.projectId = projectId;
    }

    public Long getFileId() {
        return fileId;
    }

    public void setFileId(Long fileId) {
        this.fileId = fileId;
    }

    public Long getAuthorId() {
        return authorId;
    }

    public void setAuthorId(Long authorId) {
        this.authorId = authorId;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public Integer getLineNumber() {
        return lineNumber;
    }

    public void setLineNumber(Integer lineNumber) {
        this.lineNumber = lineNumber;
    }

    public Integer getColumnNumber() {
        return columnNumber;
    }

    public void setColumnNumber(Integer columnNumber) {
        this.columnNumber = columnNumber;
    }

    public Long getParentCommentId() {
        return parentCommentId;
    }

    public void setParentCommentId(Long parentCommentId) {
        this.parentCommentId = parentCommentId;
    }

    public Boolean getResolved() {
        return resolved;
    }

    public void setResolved(Boolean resolved) {
        this.resolved = resolved;
    }

    public Long getSnapshotId() {
        return snapshotId;
    }

    public void setSnapshotId(Long snapshotId) {
        this.snapshotId = snapshotId;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
