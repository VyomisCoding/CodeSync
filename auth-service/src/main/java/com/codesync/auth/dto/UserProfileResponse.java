package com.codesync.auth.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class UserProfileResponse {
    private Long userId;
    private String username;
    private String email;
    private String fullName;
    private String role;
    private String avatarUrl;
    private String provider;
    private boolean active;
    private LocalDateTime createdAt;
    private String bio;
}
