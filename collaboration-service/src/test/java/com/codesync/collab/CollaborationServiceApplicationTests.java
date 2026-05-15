package com.codesync.collab;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = "codesync.collab.remote-validation.enabled=false")
class CollaborationServiceApplicationTests {

    @Test
    void contextLoads() {
    }
}
