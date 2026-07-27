package com.smartsoc.api.security;

import com.smartsoc.TestcontainersConfiguration;
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

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Clé de lecture dédiée aux outils du service d'assistant IA (ADR-008) :
 * fonctionne UNIQUEMENT sur les endpoints GET, jamais sur l'écriture.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "smartsoc.security.bootstrap-admin.password=IntegrationTest123!",
                "smartsoc.security.ai-tools.api-key=test-ai-tools-key-0123456789abcdef",
        })
@Import(TestcontainersConfiguration.class)
class AiToolsApiKeyFilterIntegrationTest {

    private static final String VALID_KEY = "test-ai-tools-key-0123456789abcdef";

    @Autowired
    private TestRestTemplate rest;

    @Test
    void validKeyAuthenticatesAGetEndpoint() {
        ResponseEntity<String> response = getWithKey("/api/v1/mitre/tactics", VALID_KEY);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void missingKeyIsRejected() {
        ResponseEntity<String> response = rest.exchange("/api/v1/mitre/tactics",
                HttpMethod.GET, new HttpEntity<>(new HttpHeaders()), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void wrongKeyIsRejected() {
        ResponseEntity<String> response = getWithKey("/api/v1/mitre/tactics", "not-the-right-key");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void keyDoesNotAuthenticateWriteEndpoints() {
        // Le filtre ignore tout ce qui n'est pas GET (shouldNotFilter) : sur
        // un POST, aucune authentification n'est posée, quelle que soit la
        // clé fournie — la requête est traitée comme anonyme.
        HttpHeaders headers = new HttpHeaders();
        headers.set(AiToolsApiKeyFilter.API_KEY_HEADER, VALID_KEY);
        ResponseEntity<String> response = rest.exchange(
                "/api/v1/alerts/" + UUID.randomUUID() + "/classify",
                HttpMethod.POST, new HttpEntity<>(headers), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    private ResponseEntity<String> getWithKey(String url, String key) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(AiToolsApiKeyFilter.API_KEY_HEADER, key);
        return rest.exchange(url, HttpMethod.GET, new HttpEntity<>(headers), String.class);
    }
}
