package com.codesync.file.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class MoveRequest {
    @Size(max = 512, message = "New parent path must be at most 512 characters")
    private String newParentPath;
}
