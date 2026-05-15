package com.codesync.project;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ProjectApiSecurityTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void myProjectsRequiresToken() throws Exception {
        mockMvc.perform(get("/projects/my"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void memberProjectsRequireToken() throws Exception {
        mockMvc.perform(get("/projects/member/1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void publicProjectsRemainPublic() throws Exception {
        mockMvc.perform(get("/projects/public"))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
    }
}
