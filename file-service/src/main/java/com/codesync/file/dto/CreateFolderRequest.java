package com.codesync.file.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateFolderRequest {
    @NotNull(message = "Project ID is required")
    private Long projectId;

    @NotBlank(message = "Folder name is required")
    @Size(max = 255, message = "Folder name must be at most 255 characters")
    private String folderName;

    @Size(max = 512, message = "Parent path must be at most 512 characters")
    private String parentPath;

}
