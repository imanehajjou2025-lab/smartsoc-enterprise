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
 * Sauvegarde PostgreSQL : le vrai succès de {@code pg_dump} se vérifie
 * manuellement (aucun client pg_dump sur la machine de CI/dev, voir
 * {@code PgDumpBackupAdapterTest}) — cette suite couvre le RBAC et la
 * dégradation propre (503) quand l'outil est indisponible, en forçant une
 * commande inexistante pour un résultat déterministe quel que soit
 * l'environnement d'exécution.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "smartsoc.security.bootstrap-admin.password=IntegrationTest123!",
                "smartsoc.backup.pg-dump-command=pg_dump_test_stub_does_not_exist"
        })
@Import(TestcontainersConfiguration.class)
class BackupControllerIntegrationTest {

    private static final String BACKUP = "/api/v1/settings/backup";

    @Autowired
    private TestRestTemplate rest;

    @Test
    void adminGetsA503WhenPgDumpIsUnavailable() {
        String adminToken = loginToken("admin", "IntegrationTest123!");

        ResponseEntity<String> response = rest.exchange(BACKUP, HttpMethod.GET,
                new HttpEntity<>(bearerHeaders(adminToken)), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody()).contains("BACKUP_EXECUTION_FAILED");
    }

    @Test
    void nonAdminIsForbiddenByRbac() {
        String adminToken = loginToken("admin", "IntegrationTest123!");
        rest.exchange("/api/v1/users", HttpMethod.POST,
                new HttpEntity<>(Map.of("username", "analyst.backup", "email", "analyst.backup@smartsoc.io",
                        "password", "Str0ng-Passw0rd!", "fullName", "Analyst Backup", "role", "SOC_ANALYST"),
                        bearerHeaders(adminToken)),
                String.class);
        String analystToken = loginToken("analyst.backup", "Str0ng-Passw0rd!");

        ResponseEntity<String> forbidden = rest.exchange(BACKUP, HttpMethod.GET,
                new HttpEntity<>(bearerHeaders(analystToken)), String.class);

        assertThat(forbidden.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(forbidden.getBody()).contains("ACCESS_DENIED");
    }

    private String loginToken(String username, String password) {
        return rest.postForEntity("/api/v1/auth/login",
                        Map.of("username", username, "password", password), TokenResponse.class)
                .getBody().accessToken();
    }

    private static HttpHeaders bearerHeaders(String bearer) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(bearer);
        return headers;
    }
}
