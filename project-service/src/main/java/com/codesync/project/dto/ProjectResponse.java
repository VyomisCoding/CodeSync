package com.codesync.project.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class ProjectResponse {

    private Long projectId;

    private Long ownerId;

    private String name;

    private String description;

    private String language;

    private String visibility;

    private String templateId;

    private Boolean isArchived;

    private Integer starCount;

    private Integer forkCount;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
