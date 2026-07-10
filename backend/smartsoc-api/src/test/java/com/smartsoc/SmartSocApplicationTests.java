package com.smartsoc;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class SmartSocApplicationTests {

    @Test
    void contextLoads() {
        // Verifies that the Spring context assembles correctly against a
        // real PostgreSQL (Flyway migrations + JPA mapping validation
        // included). Guards against wiring/configuration errors.
    }
}
