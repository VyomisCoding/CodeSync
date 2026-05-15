package com.codesync.execution;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ExecutionControllerSecurityTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void executionEndpointsRequireAuthentication() throws Exception {
        mockMvc.perform(get("/executions/supportedLanguages"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/executions/submit")
                        .contentType("application/json")
                        .content("""
                                {
                                  "projectId": 1,
                                  "fileId": 1,
                                  "language": "java",
                                  "sourceCode": "public class Main { public static void main(String[] args) { System.out.println(\\"hi\\"); } }",
                                  "stdin": ""
                                }
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void healthEndpointIsPublic() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());
    }
}
