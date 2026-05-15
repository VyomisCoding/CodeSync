package com.codesync.project.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ProjectRequest {

    @NotBlank(message = "Project name is required")
    @Size(max = 120, message = "Project name must be at most 120 characters")
    private String name;

    @Size(max = 5000, message = "Description must be at most 5000 characters")
    private String description;

    @Size(max = 80, message = "Language must be at most 80 characters")
    private String language;

    @NotBlank(message = "Visibility is required")
    @Pattern(regexp = "(?i)PUBLIC|PRIVATE", message = "Visibility must be PUBLIC or PRIVATE")
    private String visibility;   // PUBLIC / PRIVATE

    @Size(max = 120, message = "Template ID must be at most 120 characters")
    private String templateId;
}
