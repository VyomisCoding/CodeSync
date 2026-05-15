package com.codesync.file.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UpdateContentRequest {
    @NotNull(message = "Content is required")
    private String content;
}
