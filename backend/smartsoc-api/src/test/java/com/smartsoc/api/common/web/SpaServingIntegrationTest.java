package com.smartsoc.api.common.web;

import com.smartsoc.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Vérifie que Spring Boot sert bien la SPA React (ADR-007, sans Nginx) :
 * fichiers statiques réels, fallback index.html pour les routes client,
 * et exclusion des préfixes techniques. Utilise les stubs de
 * src/test/resources/static/.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "smartsoc.security.bootstrap-admin.password=IntegrationTest123!")
@Import(TestcontainersConfiguration.class)
class SpaServingIntegrationTest {

    @Autowired
    private TestRestTemplate rest;

    @Test
    void servesTheSpaShellAtRoot() {
        ResponseEntity<String> response = rest.getForEntity("/", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("SMARTSOC_SPA_STUB");
    }

    @Test
    void fallsBackToIndexForClientRoutes() {
        // Route React inconnue du backend : doit renvoyer la coquille SPA,
        // pas un 404 (sinon le rechargement d'une page casse).
        for (String route : new String[] {"/alerts", "/admin/users", "/dashboard"}) {
            ResponseEntity<String> response = rest.getForEntity(route, String.class);
            assertThat(response.getStatusCode()).as(route).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).as(route).contains("SMARTSOC_SPA_STUB");
        }
    }

    @Test
    void servesRealStaticAssets() {
        ResponseEntity<String> response = rest.getForEntity("/assets/app.js", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("stub asset");
    }

    @Test
    void doesNotHijackTechnicalPrefixes() {
        // Un asset webjar inexistant ne doit PAS retomber sur index.html :
        // le résolveur SPA exclut ces préfixes (404 attendu).
        ResponseEntity<String> webjar = rest.getForEntity("/webjars/does-not-exist.js", String.class);
        assertThat(webjar.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        // L'API reste protégée, jamais servie comme une route SPA.
        ResponseEntity<String> api = rest.getForEntity("/api/v1/auth/me", String.class);
        assertThat(api.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
