package com.smartsoc.api.audit;

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
 * Journal d'audit contre un vrai PostgreSQL : une connexion échouée puis
 * réussie laisse deux traces distinctes, consultables et filtrables ;
 * l'accès reste réservé à l'ADMIN (même RBAC que /api/v1/users).
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "smartsoc.security.bootstrap-admin.password=IntegrationTest123!")
@Import(TestcontainersConfiguration.class)
class AuditLogControllerIntegrationTest {

    private static final String AUDIT_LOGS = "/api/v1/audit-logs";
    private static final String STRONG_PASSWORD = "Str0ng-Passw0rd!";

    @Autowired
    private TestRestTemplate rest;

    @Test
    void loginAttemptsAreTracedAndQueryableByAnAdmin() {
        // Un mot de passe erroné...
        rest.postForEntity("/api/v1/auth/login",
                Map.of("username", "admin", "password", "wrong"), String.class);
        // ...puis une connexion reussie.
        String adminToken = loginToken("admin", "IntegrationTest123!");

        ResponseEntity<String> failedLogins = exchange(HttpMethod.GET,
                AUDIT_LOGS + "?action=LOGIN_FAILED", adminToken, String.class);
        assertThat(failedLogins.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(failedLogins.getBody()).contains("\"action\":\"LOGIN_FAILED\"")
                .contains("\"actorUsername\":\"admin\"");

        ResponseEntity<String> succeededLogins = exchange(HttpMethod.GET,
                AUDIT_LOGS + "?action=LOGIN_SUCCEEDED", adminToken, String.class);
        assertThat(succeededLogins.getBody()).contains("\"action\":\"LOGIN_SUCCEEDED\"");
    }

    @Test
    void nonAdminIsForbiddenByRbac() {
        String adminToken = loginToken("admin", "IntegrationTest123!");
        exchange(HttpMethod.POST, "/api/v1/users", adminToken,
                Map.of("username", "analyst.audit", "email", "analyst.audit@smartsoc.io",
                        "password", STRONG_PASSWORD, "fullName", "Analyst Audit", "role", "SOC_ANALYST"),
                String.class);
        String analystToken = loginToken("analyst.audit", STRONG_PASSWORD);

        ResponseEntity<String> forbidden = exchange(HttpMethod.GET, AUDIT_LOGS, analystToken, String.class);

        assertThat(forbidden.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(forbidden.getBody()).contains("ACCESS_DENIED");
    }

    private String loginToken(String username, String password) {
        return rest.postForEntity("/api/v1/auth/login",
                        Map.of("username", username, "password", password), TokenResponse.class)
                .getBody().accessToken();
    }

    private <T> ResponseEntity<T> exchange(HttpMethod method, String url, String bearer, Class<T> type) {
        return exchange(method, url, bearer, null, type);
    }

    private <T> ResponseEntity<T> exchange(HttpMethod method, String url, String bearer,
                                           Object body, Class<T> type) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(bearer);
        return rest.exchange(url, method, new HttpEntity<>(body, headers), type);
    }
}
