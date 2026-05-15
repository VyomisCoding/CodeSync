package com.codesync.collab;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "codesync.collab.remote-validation.enabled=false")
@AutoConfigureMockMvc
class CollabControllerSecurityTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void sessionsEndpointsRequireAuthentication() throws Exception {
        mockMvc.perform(get("/sessions/project/1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void healthEndpointIsPublic() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());
    }
}
