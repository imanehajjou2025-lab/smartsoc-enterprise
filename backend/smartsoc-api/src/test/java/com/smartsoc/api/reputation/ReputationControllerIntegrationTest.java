package com.smartsoc.api.reputation;

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
 * {@code GET /api/v1/reputation} contre un vrai PostgreSQL, mode
 * simulation (ADR-014 phase 3) : le stub simulé est déterministe
 * (valeur contenant « evil »/« malicious »/« phishing » = suspect).
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "smartsoc.security.bootstrap-admin.password=IntegrationTest123!")
@Import(TestcontainersConfiguration.class)
class ReputationControllerIntegrationTest {

    private static final String STRONG_PASSWORD = "Str0ng-Passw0rd!";

    @Autowired
    private TestRestTemplate rest;

    @Test
    void returnsAMaliciousVerdictForASuspiciousLookingValue() {
        String admin = loginToken("admin", "IntegrationTest123!");

        ResponseEntity<String> response = exchange(HttpMethod.GET,
                "/api/v1/reputation?type=DOMAIN&value=evil-phishing-test.example", admin);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody())
                .contains("\"verdict\":\"MALICIOUS\"")
                .contains("\"source\":\"virustotal\"");
    }

    @Test
    void returnsAHarmlessVerdictForAnOrdinaryValue() {
        String admin = loginToken("admin", "IntegrationTest123!");

        ResponseEntity<String> response = exchange(HttpMethod.GET,
                "/api/v1/reputation?type=IPV4&value=203.0.113.9", admin);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("\"verdict\":\"HARMLESS\"");
    }

    @Test
    void emailIsRejectedWithAnHonest422RatherThanAFabricatedResult() {
        String admin = loginToken("admin", "IntegrationTest123!");

        ResponseEntity<String> response = exchange(HttpMethod.GET,
                "/api/v1/reputation?type=EMAIL&value=attacker@evil.test", admin);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Test
    void aRepeatedLookupServesTheCacheRatherThanCreatingADuplicateRow() {
        String admin = loginToken("admin", "IntegrationTest123!");

        ResponseEntity<String> first = exchange(HttpMethod.GET,
                "/api/v1/reputation?type=IPV4&value=203.0.113.55", admin);
        ResponseEntity<String> second = exchange(HttpMethod.GET,
                "/api/v1/reputation?type=IPV4&value=203.0.113.55", admin);

        String firstId = first.getBody().split("\"id\":\"")[1].split("\"")[0];
        String secondId = second.getBody().split("\"id\":\"")[1].split("\"")[0];
        assertThat(secondId).isEqualTo(firstId);
    }

    @Test
    void nonAnalystIsForbiddenByRbac() {
        String admin = loginToken("admin", "IntegrationTest123!");
        exchange(HttpMethod.POST, "/api/v1/users", admin,
                Map.of("username", "viewer.reputation", "email", "viewer.reputation@smartsoc.io",
                        "password", STRONG_PASSWORD, "fullName", "Viewer Reputation", "role", "VIEWER"),
                String.class);
        String viewerToken = loginToken("viewer.reputation", STRONG_PASSWORD);

        ResponseEntity<String> response = exchange(HttpMethod.GET,
                "/api/v1/reputation?type=IPV4&value=203.0.113.99", viewerToken);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    private String loginToken(String username, String password) {
        return rest.postForEntity("/api/v1/auth/login",
                        Map.of("username", username, "password", password), TokenResponse.class)
                .getBody().accessToken();
    }

    private ResponseEntity<String> exchange(HttpMethod method, String url, String bearer) {
        return exchange(method, url, bearer, null, String.class);
    }

    private <T> ResponseEntity<T> exchange(HttpMethod method, String url, String bearer,
                                           Object body, Class<T> type) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(bearer);
        return rest.exchange(url, method, new HttpEntity<>(body, headers), type);
    }
}
