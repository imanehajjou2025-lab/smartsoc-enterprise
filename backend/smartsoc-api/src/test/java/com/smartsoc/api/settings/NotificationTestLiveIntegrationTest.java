package com.smartsoc.api.settings;

import com.smartsoc.TestcontainersConfiguration;
import com.smartsoc.api.identity.auth.dto.TokenResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mode notifications LIVE, sans SMTP réel disponible (ADR-005) : le bouton
 * « tester l'envoi » doit remonter un 503 exploitable, pas un 500 générique
 * — même doctrine que la dégradation gracieuse du classifieur IA.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "smartsoc.security.bootstrap-admin.password=IntegrationTest123!",
                "smartsoc.notifications.mode=live",
                "spring.mail.host=localhost",
                "spring.mail.port=1",
                "spring.mail.properties.mail.smtp.connectiontimeout=500",
                "spring.mail.properties.mail.smtp.timeout=500"
        })
@Import(TestcontainersConfiguration.class)
class NotificationTestLiveIntegrationTest {

    @Autowired
    private TestRestTemplate rest;

    @Test
    void testEmailFailsWith503WhenSmtpIsUnreachable() {
        String adminToken = loginToken("admin", "IntegrationTest123!");
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(adminToken);

        ResponseEntity<String> response = rest.exchange("/api/v1/settings/notifications/test", HttpMethod.POST,
                new HttpEntity<>(Map.of("recipientEmail", "admin@smartsoc.local"), headers), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody()).contains("NOTIFICATION_TEST_FAILED");
    }

    private String loginToken(String username, String password) {
        return rest.postForEntity("/api/v1/auth/login",
                        Map.of("username", username, "password", password), TokenResponse.class)
                .getBody().accessToken();
    }
}
