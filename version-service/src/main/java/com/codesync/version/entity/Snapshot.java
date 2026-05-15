package com.codesync.version.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "snapshots",
        indexes = {
                @Index(name = "idx_snapshots_project_created", columnList = "project_id,created_at"),
                @Index(name = "idx_snapshots_file_created", columnList = "file_id,created_at"),
                @Index(name = "idx_snapshots_branch_created", columnList = "branch_name,created_at"),
                @Index(name = "idx_snapshots_hash", columnList = "snapshot_hash"),
                @Index(name = "idx_snapshots_tag", columnList = "snapshot_tag")
        }
)
public class Snapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "snapshot_id")
    private Long snapshotId;

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Column(name = "file_id", nullable = false)
    private Long fileId;

    @Column(name = "author_id", nullable = false)
    private Long authorId;

    @Column(name = "message", nullable = false, length = 255)
    private String message;

    @Lob
    @Column(name = "content", nullable = false, columnDefinition = "LONGTEXT")
    private String content;

    @Column(name = "snapshot_hash", nullable = false, length = 64)
    private String hash;

    @Column(name = "parent_snapshot_id")
    private Long parentSnapshotId;

    @Column(name = "branch_name", nullable = false, length = 100)
    private String branch;

    @Column(name = "snapshot_tag", length = 100)
    private String tag;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (branch == null || branch.isBlank()) {
            branch = "main";
        }
    }

    public Long getSnapshotId() {
        return snapshotId;
    }

    public void setSnapshotId(Long snapshotId) {
        this.snapshotId = snapshotId;
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

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getHash() {
        return hash;
    }

    public void setHash(String hash) {
        this.hash = hash;
    }

    public Long getParentSnapshotId() {
        return parentSnapshotId;
    }

    public void setParentSnapshotId(Long parentSnapshotId) {
        this.parentSnapshotId = parentSnapshotId;
    }

    public String getBranch() {
        return branch;
    }

    public void setBranch(String branch) {
        this.branch = branch;
    }

    public String getTag() {
        return tag;
    }

    public void setTag(String tag) {
        this.tag = tag;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
