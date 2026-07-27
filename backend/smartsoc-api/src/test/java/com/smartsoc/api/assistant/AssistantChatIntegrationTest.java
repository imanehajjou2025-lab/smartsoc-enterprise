package com.smartsoc.api.assistant;

import com.smartsoc.TestcontainersConfiguration;
import com.smartsoc.api.assistant.dto.AssistantDtos.ChatResponse;
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
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Dialogue assistant de bout en bout en mode simulation (le mode par
 * défaut) : tout utilisateur authentifié peut dialoguer (lecture seule,
 * ADR-003), l'historique est intégralement fourni par l'appelant (le
 * backend reste sans état, ADR-008).
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "smartsoc.security.bootstrap-admin.password=IntegrationTest123!",
        })
@Import(TestcontainersConfiguration.class)
class AssistantChatIntegrationTest {

    @Autowired
    private TestRestTemplate rest;

    @Test
    void authenticatedUserGetsASimulatedReply() {
        ResponseEntity<ChatResponse> response = chat(adminToken(), Map.of(
                "messages", List.of(Map.of("role", "user", "content", "Bonjour, explique-moi T1110."))));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().reply()).contains("T1110");
        assertThat(response.getBody().model()).isEqualTo("simulation");
        assertThat(response.getBody().generatedAt()).isNotNull();
    }

    @Test
    void contextSummaryIsRelayedWithoutBeingRebuiltServerSide() {
        ResponseEntity<ChatResponse> response = chat(adminToken(), Map.of(
                "messages", List.of(Map.of("role", "user", "content", "Résume cette alerte.")),
                "context", Map.of("summary", "Alerte CRITICAL sur srv-web-01, MITRE T1110")));

        assertThat(response.getBody().reply()).contains("Alerte CRITICAL sur srv-web-01");
    }

    @Test
    void emptyMessageListIsRejected() {
        ResponseEntity<String> response = rest.exchange("/api/v1/assistant/chat", HttpMethod.POST,
                new HttpEntity<>(Map.of("messages", List.of()), authHeaders(adminToken())),
                String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void unauthenticatedRequestIsRejected() {
        ResponseEntity<String> response = rest.exchange("/api/v1/assistant/chat", HttpMethod.POST,
                new HttpEntity<>(Map.of("messages",
                        List.of(Map.of("role", "user", "content", "Bonjour"))), jsonHeaders()),
                String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // --- helpers ---

    private ResponseEntity<ChatResponse> chat(String token, Map<String, Object> body) {
        return rest.exchange("/api/v1/assistant/chat", HttpMethod.POST,
                new HttpEntity<>(body, authHeaders(token)), ChatResponse.class);
    }

    private HttpHeaders authHeaders(String token) {
        HttpHeaders headers = jsonHeaders();
        headers.setBearerAuth(token);
        return headers;
    }

    private HttpHeaders jsonHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    private String adminToken() {
        return rest.postForEntity("/api/v1/auth/login",
                        Map.of("username", "admin", "password", "IntegrationTest123!"),
                        TokenResponse.class)
                .getBody().accessToken();
    }
}
