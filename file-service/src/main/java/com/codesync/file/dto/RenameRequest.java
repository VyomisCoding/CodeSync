package com.codesync.file.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RenameRequest {
    @NotBlank(message = "New name is required")
    @Size(max = 255, message = "New name must be at most 255 characters")
    private String newName;
}
