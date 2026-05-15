package com.codesync.file.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateFileRequest {
    @NotNull(message = "Project ID is required")
    private Long projectId;

    @NotBlank(message = "File name is required")
    @Size(max = 255, message = "File name must be at most 255 characters")
    private String name;

    @Size(max = 512, message = "Parent path must be at most 512 characters")
    private String parentPath;

    @Size(max = 80, message = "Language must be at most 80 characters")
    private String language;

}
