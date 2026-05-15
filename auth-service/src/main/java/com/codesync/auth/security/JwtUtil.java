package com.codesync.auth.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.security.Keys;
import com.codesync.auth.entity.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class JwtUtil {

    private static final String ACCESS_TOKEN = "ACCESS";
    private static final String REFRESH_TOKEN = "REFRESH";

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expiration-ms:86400000}")
    private long expirationMs;

    @Value("${jwt.refresh-secret:${jwt.secret}}")
    private String refreshSecret;

    @Value("${jwt.refresh-expiration-ms:604800000}")
    private long refreshExpirationMs;

    private SecretKey getAccessKey() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    private SecretKey getRefreshKey() {
        return Keys.hmacShaKeyFor(refreshSecret.getBytes(StandardCharsets.UTF_8));
    }

    public String generateToken(User user) {
        return generateAccessToken(user);
    }

    public String generateAccessToken(User user) {
        return generateToken(user, ACCESS_TOKEN, getAccessKey(), expirationMs);
    }

    public String generateRefreshToken(User user) {
        return generateToken(user, REFRESH_TOKEN, getRefreshKey(), refreshExpirationMs);
    }

    private String generateToken(User user, String tokenType, SecretKey signingKey, long ttlMs) {
        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("userId", user.getUserId());
        claims.put("email", user.getEmail());
        claims.put("username", user.getUsername());
        claims.put("role", user.getRole().name());
        claims.put("provider", user.getProvider().name());
        claims.put("tokenType", tokenType);

        return Jwts.builder()
                .claims(claims)
                .subject(String.valueOf(user.getUserId()))
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + ttlMs))
                .signWith(signingKey)
                .compact();
    }

    public Long extractUserId(String token) {
        return extractUserId(token, getAccessKey());
    }

    public Long extractUserIdFromRefreshToken(String token) {
        return extractUserId(token, getRefreshKey());
    }

    private Long extractUserId(String token, SecretKey key) {
        return Long.valueOf(Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .getSubject());
    }

    public String extractEmail(String token) {
        return Jwts.parser()
                .verifyWith(getAccessKey())
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .get("email", String.class);
    }

    public String extractRole(String token) {
        return Jwts.parser()
                .verifyWith(getAccessKey())
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .get("role", String.class);
    }

    public boolean isTokenValid(String token) {
        return isTokenValid(token, ACCESS_TOKEN, getAccessKey());
    }

    public boolean isRefreshTokenValid(String token) {
        return isTokenValid(token, REFRESH_TOKEN, getRefreshKey());
    }

    private boolean isTokenValid(String token, String expectedTokenType, SecretKey key) {
        try {
            String tokenType = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload()
                    .get("tokenType", String.class);
            return expectedTokenType.equals(tokenType);
        } catch (JwtException | IllegalArgumentException ex) {
            return false;
        }
    }

    public long getExpirationMs() {
        return expirationMs;
    }

    public long getRefreshExpirationMs() {
        return refreshExpirationMs;
    }
}
