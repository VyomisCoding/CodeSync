package com.codesync.auth.controller;

import com.codesync.auth.dto.AuthResponse;
import com.codesync.auth.dto.ChangePasswordRequest;
import com.codesync.auth.dto.LoginRequest;
import com.codesync.auth.dto.MessageResponse;
import com.codesync.auth.dto.RegisterRequest;
import com.codesync.auth.dto.UpdateProfileRequest;
import com.codesync.auth.dto.UserProfileResponse;
import com.codesync.auth.dto.UserSummaryResponse;
import com.codesync.auth.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@Tag(name = "Auth Service", description = "Authentication, profile, and user discovery APIs")
public class AuthController {
    private final AuthService service;

    @PostMapping("/register")
    @Operation(summary = "Register a local developer account")
    public MessageResponse register(@Valid @RequestBody RegisterRequest request) {
        return service.register(request);
    }

    @PostMapping("/login")
    @Operation(summary = "Authenticate and receive a JWT")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return service.login(request);
    }

    @PostMapping("/logout")
    @Operation(summary = "Logout the current user", security = @SecurityRequirement(name = "bearerAuth"))
    public MessageResponse logout() {
        return service.logout();
    }

    @PostMapping("/refresh")
    @Operation(summary = "Exchange a valid refresh token for a new token pair")
    public AuthResponse refresh(@RequestHeader("Authorization") String authorization) {
        return service.refresh(authorization);
    }

    @GetMapping("/profile")
    @Operation(summary = "Get the current user profile", security = @SecurityRequirement(name = "bearerAuth"))
    public UserProfileResponse profile(Authentication authentication) {
        return service.profile(authenticatedUserId(authentication));
    }

    @PutMapping("/profile")
    @Operation(summary = "Update username, email, avatar, bio, and profile details", security = @SecurityRequirement(name = "bearerAuth"))
    public UserProfileResponse update(Authentication authentication, @Valid @RequestBody UpdateProfileRequest request) {
        return service.update(authenticatedUserId(authentication), request);
    }

    @PutMapping("/password")
    @Operation(summary = "Change the password for a local account", security = @SecurityRequirement(name = "bearerAuth"))
    public MessageResponse password(Authentication authentication, @Valid @RequestBody ChangePasswordRequest request) {
        return service.changePassword(authenticatedUserId(authentication), request);
    }

    @GetMapping("/search")
    @Operation(summary = "Search active users by username")
    public List<UserSummaryResponse> search(@RequestParam String keyword) {
        return service.search(keyword);
    }

    @PutMapping("/deactivate")
    @Operation(summary = "Deactivate the current account", security = @SecurityRequirement(name = "bearerAuth"))
    public MessageResponse deactivate(Authentication authentication) {
        return service.deactivate(authenticatedUserId(authentication));
    }

    private Long authenticatedUserId(Authentication authentication) {
        return Long.valueOf(authentication.getName());
    }
}
