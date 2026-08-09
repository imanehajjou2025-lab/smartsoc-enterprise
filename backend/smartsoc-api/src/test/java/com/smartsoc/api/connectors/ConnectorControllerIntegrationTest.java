package com.smartsoc.api.connectors;

import com.smartsoc.TestcontainersConfiguration;
import com.smartsoc.api.identity.auth.dto.TokenResponse;
import com.smartsoc.application.connectors.AgentSyncService;
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
 * Section « Connecteurs » de la console contre un vrai PostgreSQL —
 * synchronisation déclenchée directement (pas via le planificateur, dont
 * le délai initial rendrait ce test lent et non déterministe), RBAC
 * ADMIN identique aux autres endpoints de la console Paramètres.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "smartsoc.security.bootstrap-admin.password=IntegrationTest123!")
@Import(TestcontainersConfiguration.class)
class ConnectorControllerIntegrationTest {

    private static final String CONNECTORS = "/api/v1/connectors";
    private static final String STRONG_PASSWORD = "Str0ng-Passw0rd!";

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private AgentSyncService agentSyncService;

    @Test
    void listsWazuhWithItsLastSyncSummaryAfterASync() {
        // Mode simulation par defaut en test : 3 agents fixes (voir
        // SimulatedAgentInventoryAdapter), zero rejet attendu.
        agentSyncService.synchronize();
        String admin = loginToken("admin", "IntegrationTest123!");

        ResponseEntity<String> list = exchange(HttpMethod.GET, CONNECTORS, admin, String.class);

        assertThat(list.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(list.getBody())
                .contains("\"type\":\"WAZUH\"")
                .contains("\"status\":\"CONNECTED\"")
                .contains("\"itemsProcessed\":3")
                .contains("\"itemsRejected\":0")
                .contains("\"outcome\":\"SUCCESS\"");
    }

    @Test
    void getOneReturnsTheSameConnectorByType() {
        agentSyncService.synchronize();
        String admin = loginToken("admin", "IntegrationTest123!");

        ResponseEntity<String> one = exchange(HttpMethod.GET, CONNECTORS + "/WAZUH", admin, String.class);

        assertThat(one.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(one.getBody()).contains("\"type\":\"WAZUH\"");
    }

    @Test
    void aConnectorNeverSynchronizedIsHonestlyReported404NotFabricated() {
        // Aucun service de synchronisation VirusTotal n'existe encore dans
        // ce lot : aucune ligne n'a jamais ete creee pour lui — le 404 est
        // le comportement honnete, pas une donnee inventee pour combler.
        // (MISP ne convient plus depuis son connecteur, phase 2 : son
        // planificateur tourne des l'application context et peut deja
        // avoir cree sa ligne au moment de ce test.)
        String admin = loginToken("admin", "IntegrationTest123!");

        ResponseEntity<String> virusTotal = exchange(HttpMethod.GET, CONNECTORS + "/VIRUSTOTAL", admin, String.class);

        assertThat(virusTotal.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void nonAdminIsForbiddenByRbac() {
        String admin = loginToken("admin", "IntegrationTest123!");
        exchange(HttpMethod.POST, "/api/v1/users", admin,
                Map.of("username", "analyst.connectors", "email", "analyst.connectors@smartsoc.io",
                        "password", STRONG_PASSWORD, "fullName", "Analyst Connectors", "role", "SOC_ANALYST"),
                String.class);
        String analystToken = loginToken("analyst.connectors", STRONG_PASSWORD);

        ResponseEntity<String> forbidden = exchange(HttpMethod.GET, CONNECTORS, analystToken, String.class);

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
