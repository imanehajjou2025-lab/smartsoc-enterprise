package com.smartsoc.api.assets;

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
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * API assets de bout en bout sur PostgreSQL réel (V6 + ddl validate) :
 * conflit d'unicité en 409 RFC 9457 (même à casse différente),
 * corrélation d'alertes au hostname BRUT du webhook via la jointure
 * normalisée (et son index), tri par rang de criticité, cycle de
 * décommission (lecture seule mais corrélation préservée), et RBAC.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "smartsoc.security.bootstrap-admin.password=IntegrationTest123!",
                "smartsoc.security.ingest.api-key=test-ingest-key-0123456789abcdef",
        })
@Import(TestcontainersConfiguration.class)
class AssetApiIntegrationTest {

    private static final String ASSETS = "/api/v1/assets";
    private static final String STRONG_PWD = "Str0ng!Passw0rd123";

    @Autowired
    private TestRestTemplate rest;

    @Test
    void duplicateHostnameIs409EndToEndEvenWithDifferentCase() {
        String admin = adminToken();
        String hostname = "srv-conflict-" + suffix();

        ResponseEntity<Map> first = exchange(HttpMethod.POST, ASSETS, admin,
                assetPayload(hostname, "CRITICAL"), Map.class);
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // Même hostname en MAJUSCULES + espaces : même clé normalisée.
        ResponseEntity<String> duplicate = exchange(HttpMethod.POST, ASSETS, admin,
                assetPayload("  " + hostname.toUpperCase() + " ", "LOW"), String.class);
        assertThat(duplicate.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(duplicate.getBody())
                .contains("ASSET_ALREADY_EXISTS")
                .contains(hostname);
    }

    @Test
    @SuppressWarnings("unchecked")
    void correlatesRawWebhookHostnameThroughNormalizedJoin() {
        String admin = adminToken();
        String hostname = "srv-web-" + suffix();

        Map<String, Object> asset = exchange(HttpMethod.POST, ASSETS, admin,
                assetPayload(hostname, "HIGH"), Map.class).getBody();

        // Alerte ingérée par le VRAI webhook, hostname brut majuscules+espace,
        // et une alerte d'un autre hôte qui ne doit pas être comptée.
        ingestAlert(hostname.toUpperCase() + " ");
        ingestAlert("autre-hote-" + suffix());

        Map<String, Object> correlated = exchange(HttpMethod.GET,
                ASSETS + "/" + asset.get("id") + "/alerts", admin, null, Map.class).getBody();

        assertThat(((Number) correlated.get("totalElements")).longValue()).isEqualTo(1);
        var items = (List<Map<String, Object>>) correlated.get("items");
        // Le hostname BRUT de la source est préservé sur l'alerte.
        assertThat(items.get(0)).containsEntry("hostname", hostname.toUpperCase() + " ");
    }

    @Test
    @SuppressWarnings("unchecked")
    void listSortsByCriticalityRankNotAlphabetically() {
        String admin = adminToken();
        String prefix = "tri-" + suffix() + "-";
        // Insérés dans le désordre ; l'ordre alphabétique (CRITICAL, HIGH,
        // LOW, MEDIUM) trahirait un tri sur la chaîne.
        for (String criticality : List.of("LOW", "CRITICAL", "MEDIUM", "HIGH")) {
            exchange(HttpMethod.POST, ASSETS, admin,
                    assetPayload(prefix + criticality.toLowerCase(), criticality), Map.class);
        }

        Map<String, Object> page = exchange(HttpMethod.GET,
                ASSETS + "?search=" + prefix, admin, null, Map.class).getBody();

        var items = (List<Map<String, Object>>) page.get("items");
        assertThat(items).extracting(a -> a.get("criticality"))
                .containsExactly("CRITICAL", "HIGH", "MEDIUM", "LOW");
    }

    @Test
    void decommissionCycleKeepsCorrelationReadable() {
        String admin = adminToken();
        String hostname = "srv-legacy-" + suffix();
        Map<String, Object> asset = exchange(HttpMethod.POST, ASSETS, admin,
                assetPayload(hostname, "MEDIUM"), Map.class).getBody();
        String id = (String) asset.get("id");
        ingestAlert(hostname.toUpperCase());

        // Décommission : statut + date, puis écriture rejetée en 422.
        Map<String, Object> decommissioned = exchange(HttpMethod.POST,
                ASSETS + "/" + id + "/decommission", admin, null, Map.class).getBody();
        assertThat(decommissioned).containsEntry("status", "DECOMMISSIONED");
        assertThat(decommissioned.get("decommissionedAt")).isNotNull();

        ResponseEntity<String> rejectedUpdate = exchange(HttpMethod.PUT,
                ASSETS + "/" + id, admin, updatePayload(), String.class);
        assertThat(rejectedUpdate.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(rejectedUpdate.getBody()).contains("ASSET_DECOMMISSIONED");

        // L'historique de corrélation reste lisible.
        Map<String, Object> correlated = exchange(HttpMethod.GET,
                ASSETS + "/" + id + "/alerts", admin, null, Map.class).getBody();
        assertThat(((Number) correlated.get("totalElements")).longValue()).isEqualTo(1);

        // Réactivation : l'écriture repasse.
        exchange(HttpMethod.POST, ASSETS + "/" + id + "/reactivate", admin, null, Map.class);
        ResponseEntity<Map> accepted = exchange(HttpMethod.PUT,
                ASSETS + "/" + id, admin, updatePayload(), Map.class);
        assertThat(accepted.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(accepted.getBody()).containsEntry("displayName", "Serveur mis à jour");
    }

    @Test
    void resolvesAssetByRawHostnameNormalizedServerSide() {
        String admin = adminToken();
        String hostname = "srv-lookup-" + suffix();
        Map<String, Object> created = exchange(HttpMethod.POST, ASSETS, admin,
                assetPayload(hostname, "HIGH"), Map.class).getBody();

        // Le client envoie la valeur brute (majuscules + espaces),
        // URL-encodée ; la normalisation appartient au serveur. URI.create
        // évite le double-encodage du %20 par TestRestTemplate.
        String rawEncoded = java.net.URLEncoder.encode(
                " " + hostname.toUpperCase() + " ", java.nio.charset.StandardCharsets.UTF_8)
                .replace("+", "%20");
        HttpHeaders authHeaders = new HttpHeaders();
        authHeaders.setBearerAuth(admin);
        Map<String, Object> resolved = rest.exchange(
                java.net.URI.create(rest.getRootUri() + ASSETS + "/by-hostname/" + rawEncoded),
                HttpMethod.GET, new HttpEntity<>(authHeaders), Map.class).getBody();

        assertThat(resolved).containsEntry("id", created.get("id"));
        assertThat(resolved).containsEntry("hostname", hostname);

        // 404 = information métier : aucun actif inventorié pour ce hostname.
        ResponseEntity<String> unknown = exchange(HttpMethod.GET,
                ASSETS + "/by-hostname/hote-inconnu-" + suffix(), admin, null, String.class);
        assertThat(unknown.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(unknown.getBody()).contains("RESOURCE_NOT_FOUND");
    }

    @Test
    void viewerCanReadButCannotWrite() {
        String admin = adminToken();
        String viewer = "viewer." + UUID.randomUUID().toString().substring(0, 8);
        exchange(HttpMethod.POST, "/api/v1/users", admin, Map.of(
                "username", viewer, "email", viewer + "@smartsoc.io", "password", STRONG_PWD,
                "fullName", "Read Only", "role", "VIEWER"), String.class);
        String viewerToken = login(viewer, STRONG_PWD);

        assertThat(exchange(HttpMethod.GET, ASSETS, viewerToken, null, String.class)
                .getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(exchange(HttpMethod.POST, ASSETS, viewerToken,
                assetPayload("srv-forbidden-" + suffix(), "LOW"), String.class)
                .getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    // --- helpers ---

    private static String suffix() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private static Map<String, Object> assetPayload(String hostname, String criticality) {
        return Map.of(
                "hostname", hostname,
                "type", "SERVER",
                "criticality", criticality,
                "exposure", "INTERNAL");
    }

    private static Map<String, Object> updatePayload() {
        return Map.of(
                "displayName", "Serveur mis à jour",
                "type", "SERVER",
                "criticality", "HIGH",
                "exposure", "INTERNAL");
    }

    private void ingestAlert(String rawHostname) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-API-Key", "test-ingest-key-0123456789abcdef");
        rest.postForEntity("/api/v1/ingest/alerts", new HttpEntity<>(Map.of(
                "source", "wazuh", "externalId", "evt-asset-" + UUID.randomUUID(),
                "title", "Alerte de corrélation actif", "severity", "HIGH",
                "detectedAt", Instant.now().toString(),
                "hostname", rawHostname), headers), String.class);
    }

    private String adminToken() {
        return login("admin", "IntegrationTest123!");
    }

    private String login(String username, String password) {
        return rest.postForEntity("/api/v1/auth/login",
                        Map.of("username", username, "password", password), TokenResponse.class)
                .getBody().accessToken();
    }

    private <T> ResponseEntity<T> exchange(HttpMethod method, String url, String token,
                                           Object body, Class<T> type) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        if (body != null) {
            headers.setContentType(MediaType.APPLICATION_JSON);
        }
        return rest.exchange(url, method, new HttpEntity<>(body, headers), type);
    }
}
