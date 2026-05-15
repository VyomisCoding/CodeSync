package com.codesync.file.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "code_files",
        indexes = {
                @Index(name = "idx_code_files_project_deleted", columnList = "project_id,is_deleted"),
                @Index(name = "idx_code_files_project_path_deleted", columnList = "project_id,path,is_deleted"),
                @Index(name = "idx_code_files_project_language_deleted", columnList = "project_id,language,is_deleted")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CodeFile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long fileId;

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(nullable = false, length = 512)
    private String path;

    @Column(nullable = false, length = 20)
    private String type; // FILE / FOLDER

    @Column(length = 80)
    private String language;

    @Lob
    @Column(columnDefinition = "LONGTEXT")
    private String content;

    private Long size;

    @Column(name = "created_by_id", nullable = false)
    private Long createdById;

    @JsonIgnore
    @Column(name = "created_by", nullable = false)
    private Long legacyCreatedById;

    @Column(name = "last_edited_by", nullable = false)
    private Long lastEditedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Builder.Default
    @Column(name = "is_deleted", nullable = false)
    private Boolean isDeleted = false;

    @PrePersist
    public void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
        if (isDeleted == null) {
            isDeleted = false;
        }
        if (size == null) {
            size = 0L;
        }
        legacyCreatedById = createdById;
        if (content == null) {
            content = "";
        }
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
        legacyCreatedById = createdById;
    }

    @PostLoad
    public void postLoad() {
        if (createdById == null) {
            createdById = legacyCreatedById;
        }
    }
}
