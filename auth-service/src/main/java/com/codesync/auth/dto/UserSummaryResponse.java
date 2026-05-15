package com.codesync.auth.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class UserSummaryResponse {
    private Long userId;
    private String username;
    private String fullName;
    private String avatarUrl;
    private String bio;
}
