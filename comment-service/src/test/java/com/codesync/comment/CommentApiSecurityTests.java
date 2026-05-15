package com.codesync.comment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.codesync.comment.repository.CommentRepository;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class CommentApiSecurityTests {

    private static final String SECRET = "01234567890123456789012345678901";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private CommentRepository commentRepository;

    @BeforeEach
    void setUp() {
        commentRepository.deleteAll();
    }

    @Test
    void commentEndpointsRequireAuthentication() throws Exception {
        mockMvc.perform(get("/comments/file/10"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/comments/add")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "projectId": 1,
                                  "fileId": 10,
                                  "content": "Please refactor this"
                                }
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void authorAndAdminPermissionsWork() throws Exception {
        String authorToken = bearer(101L, "DEVELOPER");
        String otherToken = bearer(202L, "DEVELOPER");
        String adminToken = bearer(1L, "ADMIN");

        Long commentId = createComment(authorToken, """
                {
                  "projectId": 1,
                  "fileId": 10,
                  "content": "Please simplify this method",
                  "lineNumber": 12,
                  "columnNumber": 4
                }
                """);

        mockMvc.perform(put("/comments/update/{commentId}", commentId)
                        .header("Authorization", authorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "content": "Updated review feedback"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value("Updated review feedback"))
                .andExpect(jsonPath("$.updatedAt").isNotEmpty());

        mockMvc.perform(put("/comments/update/{commentId}", commentId)
                        .header("Authorization", otherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "content": "I should not be allowed"
                                }
                                """))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/comments/resolve/{commentId}", commentId)
                        .header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resolved").value(true));

        mockMvc.perform(get("/comments/count/10")
                        .header("Authorization", authorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(1));
    }

    @Test
    void repliesAreSupportedButThirdLevelRepliesAreRejected() throws Exception {
        String authorToken = bearer(101L, "DEVELOPER");

        Long parentId = createComment(authorToken, """
                {
                  "projectId": 1,
                  "fileId": 20,
                  "content": "Top level review comment",
                  "lineNumber": 40
                }
                """);

        Long replyId = createComment(authorToken, """
                {
                  "projectId": 1,
                  "fileId": 20,
                  "content": "Reply to the review comment",
                  "parentCommentId": %d
                }
                """.formatted(parentId));

        mockMvc.perform(get("/comments/replies/{commentId}", parentId)
                        .header("Authorization", authorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].commentId").value(replyId));

        mockMvc.perform(post("/comments/add")
                        .header("Authorization", authorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "projectId": 1,
                                  "fileId": 20,
                                  "content": "Third level reply should fail",
                                  "parentCommentId": %d
                                }
                                """.formatted(replyId)))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/comments/count/20")
                        .header("Authorization", authorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(2));
    }

    @Test
    void swaggerAndHealthArePublic() throws Exception {
        mockMvc.perform(get("/swagger-ui.html"))
                .andExpect(status().is3xxRedirection());

        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());
    }

    private Long createComment(String authorizationHeader, String body) throws Exception {
        String response = mockMvc.perform(post("/comments/add")
                        .header("Authorization", authorizationHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode json = objectMapper.readTree(response);
        return json.get("commentId").asLong();
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
