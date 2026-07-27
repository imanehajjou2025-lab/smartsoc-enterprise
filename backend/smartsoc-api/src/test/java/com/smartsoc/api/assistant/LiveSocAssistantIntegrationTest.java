package com.smartsoc.api.assistant;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.smartsoc.TestcontainersConfiguration;
import com.smartsoc.api.assistant.dto.AssistantDtos.ChatResponse;
import com.smartsoc.api.identity.auth.dto.TokenResponse;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
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
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.util.List;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mode live de bout en bout contre un DOUBLE du service IA (WireMock
 * rejouant le contrat docs/integration/ai-assistant-api.yaml) : le client
 * Feign transmet l'historique complet et le contexte, la clé X-API-Key, et
 * dégrade gracieusement (503 AI_UNAVAILABLE) quand le service tombe —
 * jamais les vrais services en test (ADR-005).
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "smartsoc.security.bootstrap-admin.password=IntegrationTest123!",
                "smartsoc.ai.mode=live",
        })
@Import(TestcontainersConfiguration.class)
class LiveSocAssistantIntegrationTest {

    private static final String CHAT = "/api/v1/chat";

    private static final WireMockServer AI_SERVICE =
            new WireMockServer(WireMockConfiguration.options().dynamicPort());

    @DynamicPropertySource
    static void aiServiceProperties(DynamicPropertyRegistry registry) {
        AI_SERVICE.start();
        registry.add("smartsoc.ai.assistant.url", AI_SERVICE::baseUrl);
        registry.add("smartsoc.ai.assistant.api-key", () -> "assistant-secret");
    }

    @AfterAll
    static void stopAiService() {
        AI_SERVICE.stop();
    }

    @Autowired
    private TestRestTemplate rest;

    @BeforeEach
    void resetStubs() {
        AI_SERVICE.resetAll();
    }

    @Test
    void relaysTheRealServiceReplyAndSendsTheApiKey() {
        AI_SERVICE.stubFor(post(urlEqualTo(CHAT)).willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                        {
                          "reply": "Cette alerte correspond à T1110 (Brute Force).",
                          "model": "ollama/llama3.1:8b",
                          "generatedAt": "2026-07-27T09:00:00Z"
                        }
                        """)));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(adminToken());
        ResponseEntity<ChatResponse> response = rest.exchange("/api/v1/assistant/chat",
                HttpMethod.POST, new HttpEntity<>(Map.of(
                        "messages", List.of(Map.of("role", "user", "content", "Que fait cette alerte ?")),
                        "context", Map.of("summary", "Alerte HIGH sur srv-web-01")), headers),
                ChatResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().reply()).contains("T1110");
        assertThat(response.getBody().model()).isEqualTo("ollama/llama3.1:8b");

        AI_SERVICE.verify(postRequestedFor(urlEqualTo(CHAT))
                .withHeader("X-API-Key", equalTo("assistant-secret"))
                .withRequestBody(matchingJsonPath("$.messages[0].content",
                        equalTo("Que fait cette alerte ?")))
                .withRequestBody(matchingJsonPath("$.context.summary",
                        equalTo("Alerte HIGH sur srv-web-01"))));
    }

    @Test
    void degradesGracefullyWhenTheServiceIsDown() {
        AI_SERVICE.stubFor(post(urlEqualTo(CHAT)).willReturn(aResponse()
                .withStatus(503)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"code\": \"MODEL_UNAVAILABLE\", \"message\": \"reloading\"}")));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(adminToken());
        ResponseEntity<String> response = rest.exchange("/api/v1/assistant/chat",
                HttpMethod.POST, new HttpEntity<>(Map.of("messages",
                        List.of(Map.of("role", "user", "content", "Bonjour"))), headers),
                String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody()).contains("AI_UNAVAILABLE");
    }

    private String adminToken() {
        return rest.postForEntity("/api/v1/auth/login",
                        Map.of("username", "admin", "password", "IntegrationTest123!"),
                        TokenResponse.class)
                .getBody().accessToken();
    }
}
