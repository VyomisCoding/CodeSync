package com.codesync.auth.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AuthResponse {
    private String token;
    private String refreshToken;
    private String tokenType;
    private long expiresInMs;
    private long refreshExpiresInMs;
    private UserProfileResponse user;
}
