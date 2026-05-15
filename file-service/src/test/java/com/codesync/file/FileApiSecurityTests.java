package com.codesync.file;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class FileApiSecurityTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void fileWriteRoutesRequireToken() throws Exception {
        mockMvc.perform(post("/files")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "projectId": 1,
                                  "name": "App.java"
                                }
                                """))
                .andExpect(status().isUnauthorized());
    }
}
