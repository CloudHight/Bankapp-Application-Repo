package com.motivalogic.bankapp;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class BankappApplicationTests {

    @Test
    void contextLoads() {
        // Verifies the Spring context starts with the test profile.
    }
}
