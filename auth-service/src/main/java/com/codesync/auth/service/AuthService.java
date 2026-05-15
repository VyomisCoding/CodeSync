package com.codesync.auth.service;

import com.codesync.auth.dto.AuthResponse;
import com.codesync.auth.dto.ChangePasswordRequest;
import com.codesync.auth.dto.LoginRequest;
import com.codesync.auth.dto.MessageResponse;
import com.codesync.auth.dto.RegisterRequest;
import com.codesync.auth.dto.UpdateProfileRequest;
import com.codesync.auth.dto.UserProfileResponse;
import com.codesync.auth.dto.UserSummaryResponse;
import com.codesync.auth.entity.AuthProvider;
import com.codesync.auth.entity.User;
import com.codesync.auth.entity.UserRole;
import com.codesync.auth.repository.UserRepository;
import com.codesync.auth.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;

@Service
@RequiredArgsConstructor
@Transactional
public class AuthService {
    private final UserRepository repo;
    private final PasswordEncoder encoder;
    private final JwtUtil jwt;

    public MessageResponse register(RegisterRequest r){
        String normalizedEmail = normalizeEmail(r.getEmail());
        String normalizedUsername = normalizeUsername(r.getUsername());

        if(repo.existsByEmail(normalizedEmail)) throw new IllegalArgumentException("Email already exists");
        if(repo.findByUsernameIgnoreCase(normalizedUsername).isPresent()) {
            throw new IllegalArgumentException("Username already exists");
        }

        User u = User.builder()
                .email(normalizedEmail)
                .username(normalizedUsername)
                .passwordHash(encoder.encode(r.getPassword()))
                .role(UserRole.DEVELOPER)
                .provider(AuthProvider.LOCAL)
                .active(true)
                .build();

        repo.save(u);
        return new MessageResponse("Registered successfully");
    }

    public AuthResponse login(LoginRequest r){
        User u = repo.findByEmail(normalizeEmail(r.getEmail()))
                .orElseThrow(() -> new IllegalArgumentException("Invalid credentials"));

        if(!u.isActive()) throw new IllegalArgumentException("Account is deactivated");
        if (u.getProvider() != AuthProvider.LOCAL) {
            throw new IllegalArgumentException("This account uses " + u.getProvider().name() + " sign-in");
        }
        if(!encoder.matches(r.getPassword(), u.getPasswordHash())) throw new IllegalArgumentException("Invalid credentials");

        return authResponse(u);
    }

    @Transactional(readOnly = true)
    public UserProfileResponse profile(Long userId){
        return mapProfile(findUserById(userId));
    }

    public UserProfileResponse update(Long userId, UpdateProfileRequest r){
        User u = findUserById(userId);

        if (r.getUsername() != null && !r.getUsername().isBlank()) {
            String username = normalizeUsername(r.getUsername());
            repo.findByUsernameIgnoreCase(username)
                    .filter(existing -> !existing.getUserId().equals(u.getUserId()))
                    .ifPresent(existing -> {
                        throw new IllegalArgumentException("Username already exists");
                    });
            u.setUsername(username);
        }

        if (r.getEmail() != null && !r.getEmail().isBlank()) {
            String email = normalizeEmail(r.getEmail());
            repo.findByEmail(email)
                    .filter(existing -> !existing.getUserId().equals(u.getUserId()))
                    .ifPresent(existing -> {
                        throw new IllegalArgumentException("Email already exists");
                    });
            u.setEmail(email);
        }

        u.setFullName(trimToNull(r.getFullName()));
        u.setBio(trimToNull(r.getBio()));
        u.setAvatarUrl(trimToNull(r.getAvatarUrl()));
        return mapProfile(repo.save(u));
    }

    public MessageResponse changePassword(Long userId, ChangePasswordRequest r){
        User u = findUserById(userId);
        if (u.getProvider() != AuthProvider.LOCAL) {
            throw new IllegalArgumentException("Password changes are only supported for local accounts");
        }
        if(!encoder.matches(r.getOldPassword(), u.getPasswordHash())) {
            throw new IllegalArgumentException("Wrong old password");
        }
        u.setPasswordHash(encoder.encode(r.getNewPassword()));
        repo.save(u);
        return new MessageResponse("Password updated");
    }

    @Transactional(readOnly = true)
    public List<UserSummaryResponse> search(String keyword){
        String cleanKeyword = keyword == null ? "" : keyword.trim();
        return repo.searchByUsername(cleanKeyword).stream()
                .filter(User::isActive)
                .map(this::mapSummary)
                .toList();
    }

    public MessageResponse deactivate(Long userId){
        User u = findUserById(userId);
        u.setActive(false);
        repo.save(u);
        return new MessageResponse("Account deactivated");
    }

    @Transactional(readOnly = true)
    public AuthResponse refresh(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new IllegalArgumentException("Authorization header is missing or invalid");
        }

        String token = authHeader.substring(7);
        if (!jwt.isRefreshTokenValid(token)) {
            throw new IllegalArgumentException("Refresh token is invalid or expired");
        }

        User user = findUserById(jwt.extractUserIdFromRefreshToken(token));
        if (!user.isActive()) {
            throw new IllegalArgumentException("Account is deactivated");
        }

        return authResponse(user);
    }

    public MessageResponse logout() {
        return new MessageResponse("Logged out successfully");
    }

    private User findUserById(Long userId) {
        return repo.findByUserId(userId)
                .orElseThrow(() -> new NoSuchElementException("User not found"));
    }

    private AuthResponse authResponse(User user) {
        return AuthResponse.builder()
                .token(jwt.generateAccessToken(user))
                .refreshToken(jwt.generateRefreshToken(user))
                .tokenType("Bearer")
                .expiresInMs(jwt.getExpirationMs())
                .refreshExpiresInMs(jwt.getRefreshExpirationMs())
                .user(mapProfile(user))
                .build();
    }

    private UserProfileResponse mapProfile(User user) {
        return UserProfileResponse.builder()
                .userId(user.getUserId())
                .username(user.getUsername())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .role(user.getRole().name())
                .avatarUrl(user.getAvatarUrl())
                .provider(user.getProvider().name())
                .active(user.isActive())
                .createdAt(user.getCreatedAt())
                .bio(user.getBio())
                .build();
    }

    private UserSummaryResponse mapSummary(User user) {
        return UserSummaryResponse.builder()
                .userId(user.getUserId())
                .username(user.getUsername())
                .fullName(user.getFullName())
                .avatarUrl(user.getAvatarUrl())
                .bio(user.getBio())
                .build();
    }

    private String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase();
    }

    private String normalizeUsername(String username) {
        return username == null ? null : username.trim();
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }

        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
