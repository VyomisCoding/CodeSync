package com.codesync.auth.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "users",
        indexes = {
                @Index(name = "idx_users_email", columnList = "email"),
                @Index(name = "idx_users_username", columnList = "username"),
                @Index(name = "idx_users_active", columnList = "active")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long userId;

    @Column(unique = true, nullable = false, length = 50)
    private String username;

    @Column(unique = true, nullable = false, length = 150)
    private String email;

    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @JsonIgnore
    @Column(name = "password", nullable = false)
    private String legacyPasswordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private UserRole role;

    @Column(length = 120)
    private String fullName;

    @Column(length = 500)
    private String avatarUrl;

    @Column(length = 500)
    private String bio;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private AuthProvider provider = AuthProvider.LOCAL;

    @Builder.Default
    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @JsonIgnore
    @Builder.Default
    @Column(name = "active", nullable = false)
    private boolean legacyActive = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        createdAt = LocalDateTime.now();
        if (provider == null) {
            provider = AuthProvider.LOCAL;
        }
        legacyPasswordHash = passwordHash;
        legacyActive = active;
    }

    @PreUpdate
    public void preUpdate() {
        legacyPasswordHash = passwordHash;
        legacyActive = active;
    }

    @PostLoad
    public void postLoad() {
        if ((passwordHash == null || passwordHash.isBlank()) && legacyPasswordHash != null) {
            passwordHash = legacyPasswordHash;
        }
        if (!active && legacyActive) {
            active = true;
        }
    }
}
