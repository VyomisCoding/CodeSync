package com.codesync.version;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class VersionControllerSecurityTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void versionEndpointsRequireAuthentication() throws Exception {
        mockMvc.perform(get("/versions/latest/1"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/versions/create")
                        .contentType("application/json")
                        .content("""
                                {
                                  "projectId": 1,
                                  "fileId": 1,
                                  "message": "Initial snapshot",
                                  "content": "class Main {}"
                                }
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void authenticatedRequestReachesController() throws Exception {
        mockMvc.perform(get("/versions/999")
                        .header("Authorization", "Bearer " + accessToken(1001L, "DEVELOPER")))
                .andExpect(status().isNotFound());
    }

    @Test
    void swaggerAndHealthArePublic() throws Exception {
        mockMvc.perform(get("/swagger-ui.html"))
                .andExpect(status().is3xxRedirection());

        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());
    }

    private String accessToken(Long userId, String role) {
        SecretKey key = Keys.hmacShaKeyFor("01234567890123456789012345678901".getBytes(StandardCharsets.UTF_8));
        Date now = new Date();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("role", role)
                .claim("tokenType", "ACCESS")
                .issuedAt(now)
                .expiration(new Date(now.getTime() + 3600_000))
                .signWith(key)
                .compact();
    }
}
