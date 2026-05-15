package com.codesync.notification;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.codesync.notification.repository.NotificationRepository;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class NotificationApiSecurityTests {

    private static final String SECRET = "01234567890123456789012345678901";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private NotificationRepository notificationRepository;

    @BeforeEach
    void setUp() {
        notificationRepository.deleteAll();
    }

    @Test
    void notificationEndpointsRequireAuthentication() throws Exception {
        mockMvc.perform(get("/notifications/recipient/10"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/notifications/send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "recipientId": 10,
                                  "type": "COMMENT",
                                  "title": "Review",
                                  "message": "Please review this change"
                                }
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void recipientLifecycleAndIsolationRulesWork() throws Exception {
        String senderToken = bearer(101L, "DEVELOPER");
        String recipientToken = bearer(202L, "DEVELOPER");
        String intruderToken = bearer(303L, "DEVELOPER");

        Long firstNotificationId = createNotification(senderToken, """
                {
                  "recipientId": 202,
                  "type": "COMMENT",
                  "title": "Comment",
                  "message": "Please review line 10",
                  "relatedId": 1,
                  "relatedType": "COMMENT"
                }
                """);

        Long secondNotificationId = createNotification(senderToken, """
                {
                  "recipientId": 202,
                  "type": "SNAPSHOT",
                  "title": "Snapshot created",
                  "message": "A new snapshot is available",
                  "relatedId": 2,
                  "relatedType": "SNAPSHOT"
                }
                """);

        mockMvc.perform(get("/notifications/recipient/{recipientId}", 202)
                        .header("Authorization", recipientToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));

        mockMvc.perform(get("/notifications/recipient/{recipientId}", 202)
                        .header("Authorization", intruderToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/notifications/unreadCount/{recipientId}", 202)
                        .header("Authorization", recipientToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unreadCount").value(2));

        mockMvc.perform(put("/notifications/read/{notificationId}", firstNotificationId)
                        .header("Authorization", recipientToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notificationId").value(firstNotificationId))
                .andExpect(jsonPath("$.isRead").value(true));

        mockMvc.perform(delete("/notifications/deleteRead/{recipientId}", 202)
                        .header("Authorization", recipientToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.affectedCount").value(1));

        mockMvc.perform(get("/notifications/unreadCount/{recipientId}", 202)
                        .header("Authorization", recipientToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unreadCount").value(1));

        mockMvc.perform(put("/notifications/readAll/{recipientId}", 202)
                        .header("Authorization", recipientToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.affectedCount").value(1));

        mockMvc.perform(get("/notifications/unreadCount/{recipientId}", 202)
                        .header("Authorization", recipientToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unreadCount").value(0));

        mockMvc.perform(delete("/notifications/{notificationId}", secondNotificationId)
                        .header("Authorization", recipientToken))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/notifications/recipient/{recipientId}", 202)
                        .header("Authorization", recipientToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void adminOnlyEndpointsAreProtected() throws Exception {
        String developerToken = bearer(101L, "DEVELOPER");
        String adminToken = bearer(1L, "ADMIN");

        mockMvc.perform(post("/notifications/sendBulk")
                        .header("Authorization", developerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "recipientIds": [10, 20],
                                  "type": "FORK",
                                  "title": "Fork created",
                                  "message": "A new fork is ready"
                                }
                                """))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/notifications/all")
                        .header("Authorization", developerToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/notifications/sendBulk")
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "recipientIds": [10, 20],
                                  "type": "FORK",
                                  "title": "Fork created",
                                  "message": "A new fork is ready"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.length()").value(2));

        mockMvc.perform(get("/notifications/all")
                        .header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void swaggerAndHealthArePublic() throws Exception {
        mockMvc.perform(get("/swagger-ui.html"))
                .andExpect(status().is3xxRedirection());

        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());
    }

    private Long createNotification(String authorizationHeader, String body) throws Exception {
        String response = mockMvc.perform(post("/notifications/send")
                        .header("Authorization", authorizationHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode json = objectMapper.readTree(response);
        return json.get("notificationId").asLong();
    }

    private String bearer(Long userId, String role) {
        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        Date now = new Date();
        String token = Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("role", role)
                .claim("tokenType", "ACCESS")
                .issuedAt(now)
                .expiration(new Date(now.getTime() + 3600_000))
                .signWith(key)
                .compact();
        return "Bearer " + token;
    }
}
