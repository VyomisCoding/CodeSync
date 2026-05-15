package com.codesync.file.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ProjectCopyRequest {

    @NotNull(message = "Target project ID is required")
    private Long targetProjectId;
}
