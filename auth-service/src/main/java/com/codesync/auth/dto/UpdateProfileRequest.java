package com.codesync.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateProfileRequest {

    @Size(min = 3, max = 50, message = "Username must be between 3 and 50 characters")
    private String username;

    @Email(message = "Email must be valid")
    @Size(max = 150, message = "Email must be at most 150 characters")
    private String email;

    @Size(max = 120, message = "Full name must be at most 120 characters")
    private String fullName;

    @Size(max = 500, message = "Bio must be at most 500 characters")
    private String bio;

    @Size(max = 500, message = "Avatar URL must be at most 500 characters")
    private String avatarUrl;
}
