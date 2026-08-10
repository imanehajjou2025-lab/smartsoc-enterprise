package com.smartsoc.api.actions;

import com.smartsoc.TestcontainersConfiguration;
import com.smartsoc.api.identity.auth.dto.TokenResponse;
import com.smartsoc.domain.assets.Asset;
import com.smartsoc.domain.assets.AssetCriticality;
import com.smartsoc.domain.assets.AssetExposure;
import com.smartsoc.domain.assets.AssetRepository;
import com.smartsoc.domain.assets.AssetType;
import com.smartsoc.domain.audit.AuditAction;
import com.smartsoc.domain.audit.AuditLogQuery;
import com.smartsoc.domain.audit.AuditLogRepository;
import com.smartsoc.domain.common.PageQuery;
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
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contrôle d'agents (ADR-014 phase 5, EFFET RÉEL) de bout en bout sur
 * PostgreSQL réel, en mode SIMULATION (aucune vraie machine touchée par
 * un test) : confirmation de cible, motif obligatoire, plafond horaire,
 * audit systématique, RBAC.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "smartsoc.security.bootstrap-admin.password=IntegrationTest123!")
@Import(TestcontainersConfiguration.class)
class AgentActionControllerIntegrationTest {

    private static final String STRONG_PWD = "Str0ng!Passw0rd123";

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private AssetRepository assetRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    private Asset seedWazuhManagedAsset(String suffix) {
        Asset asset = Asset.register(Asset.RegistrationData.builder()
                .hostname("action-target-" + suffix)
                .type(AssetType.OTHER)
                .criticality(AssetCriticality.MEDIUM)
                .exposure(AssetExposure.INTERNAL)
                .build());
        asset.applySyncMetadata("agent-" + suffix, "wazuh", "Windows 10", Instant.now());
        return assetRepository.save(asset);
    }

    @Test
    void restartsInSimulationModeAndRecordsANominativeAuditEntry() {
        String admin = adminToken();
        Asset asset = seedWazuhManagedAsset(suffix());

        ResponseEntity<Void> response = restart(asset.getId(), asset.getHostname(),
                "Agent bloque, redemarrage demande", admin);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        var entries = auditLogRepository.search(new AuditLogQuery(
                AuditAction.WAZUH_AGENT_RESTART_REQUESTED, null, null, null,
                "ASSET", asset.getId().toString(), PageQuery.of(0, 10)));
        assertThat(entries.totalElements()).isEqualTo(1);
        assertThat(entries.items().getFirst().getActorUsername()).isEqualTo("admin");
        assertThat(entries.items().getFirst().getDetails()).contains("outcome=SUCCESS");
    }

    @Test
    void rejectsWhenConfirmedHostnameDoesNotMatchTheRealTarget() {
        String admin = adminToken();
        Asset asset = seedWazuhManagedAsset(suffix());

        ResponseEntity<String> response = restart(asset.getId(), "wrong-hostname", "test", admin, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(response.getBody()).contains("ACTION_TARGET_NOT_CONFIRMED");
    }

    @Test
    void rejectsAMissingReason() {
        String admin = adminToken();
        Asset asset = seedWazuhManagedAsset(suffix());

        ResponseEntity<String> response = exchange(HttpMethod.POST,
                "/api/v1/assets/" + asset.getId() + "/restart-agent", admin,
                Map.of("confirmHostname", asset.getHostname(), "reason", ""), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void enforcesTheHourlyCapOnTheSameAsset() {
        String admin = adminToken();
        Asset asset = seedWazuhManagedAsset(suffix());

        for (int i = 0; i < 3; i++) {
            ResponseEntity<Void> ok = restart(asset.getId(), asset.getHostname(), "attempt " + i, admin);
            assertThat(ok.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        }

        ResponseEntity<String> fourth = restart(asset.getId(), asset.getHostname(), "attempt 4", admin, String.class);
        assertThat(fourth.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(fourth.getBody()).contains("ACTION_RATE_LIMIT_EXCEEDED");
    }

    @Test
    void viewerIsForbiddenFromRestartingAnAgent() {
        String admin = adminToken();
        Asset asset = seedWazuhManagedAsset(suffix());
        String viewerUsername = "viewer." + suffix();
        exchange(HttpMethod.POST, "/api/v1/users", admin, Map.of(
                "username", viewerUsername, "email", viewerUsername + "@smartsoc.io", "password", STRONG_PWD,
                "fullName", "Read Only", "role", "VIEWER"), String.class);
        String viewerToken = login(viewerUsername, STRONG_PWD);

        ResponseEntity<String> response = restart(asset.getId(), asset.getHostname(), "test", viewerToken, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    // --- block-ip (active-response firewall-drop) ---

    @Test
    void blocksTheIpInSimulationModeAndRecordsANominativeAuditEntry() {
        String admin = adminToken();
        Asset asset = seedWazuhManagedAsset(suffix());

        ResponseEntity<Void> response = blockIp(asset.getId(), asset.getHostname(), "203.0.113.42",
                "IP malveillante", admin);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        var entries = auditLogRepository.search(new AuditLogQuery(
                AuditAction.WAZUH_AGENT_FIREWALL_DROP_REQUESTED, null, null, null,
                "ASSET", asset.getId().toString(), PageQuery.of(0, 10)));
        assertThat(entries.totalElements()).isEqualTo(1);
        assertThat(entries.items().getFirst().getActorUsername()).isEqualTo("admin");
        assertThat(entries.items().getFirst().getDetails())
                .contains("outcome=SUCCESS").contains("ip=203.0.113.42");
    }

    @Test
    void rejectsBlockIpWhenConfirmedHostnameDoesNotMatchTheRealTarget() {
        String admin = adminToken();
        Asset asset = seedWazuhManagedAsset(suffix());

        ResponseEntity<String> response = blockIp(asset.getId(), "wrong-hostname", "203.0.113.42",
                "test", admin, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(response.getBody()).contains("ACTION_TARGET_NOT_CONFIRMED");
    }

    @Test
    void rejectsAnInvalidIpAddress() {
        String admin = adminToken();
        Asset asset = seedWazuhManagedAsset(suffix());

        ResponseEntity<String> response = blockIp(asset.getId(), asset.getHostname(), "not-an-ip",
                "test", admin, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(response.getBody()).contains("ACTION_INVALID_IP_ADDRESS");
    }

    @Test
    void enforcesTheHourlyCapOnTheSameAssetForBlockIp() {
        String admin = adminToken();
        Asset asset = seedWazuhManagedAsset(suffix());

        for (int i = 0; i < 3; i++) {
            ResponseEntity<Void> ok = blockIp(asset.getId(), asset.getHostname(), "203.0.113.42",
                    "attempt " + i, admin);
            assertThat(ok.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        }

        ResponseEntity<String> fourth = blockIp(asset.getId(), asset.getHostname(), "203.0.113.42",
                "attempt 4", admin, String.class);
        assertThat(fourth.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(fourth.getBody()).contains("ACTION_RATE_LIMIT_EXCEEDED");
    }

    @Test
    void viewerIsForbiddenFromBlockingAnIp() {
        String admin = adminToken();
        Asset asset = seedWazuhManagedAsset(suffix());
        String viewerUsername = "viewer." + suffix();
        exchange(HttpMethod.POST, "/api/v1/users", admin, Map.of(
                "username", viewerUsername, "email", viewerUsername + "@smartsoc.io", "password", STRONG_PWD,
                "fullName", "Read Only", "role", "VIEWER"), String.class);
        String viewerToken = login(viewerUsername, STRONG_PWD);

        ResponseEntity<String> response = blockIp(asset.getId(), asset.getHostname(), "203.0.113.42",
                "test", viewerToken, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    // --- helpers ---

    private ResponseEntity<Void> restart(UUID assetId, String confirmHostname, String reason, String token) {
        return exchange(HttpMethod.POST, "/api/v1/assets/" + assetId + "/restart-agent", token,
                Map.of("confirmHostname", confirmHostname, "reason", reason), Void.class);
    }

    private <T> ResponseEntity<T> restart(UUID assetId, String confirmHostname, String reason, String token,
                                          Class<T> type) {
        return exchange(HttpMethod.POST, "/api/v1/assets/" + assetId + "/restart-agent", token,
                Map.of("confirmHostname", confirmHostname, "reason", reason), type);
    }

    private ResponseEntity<Void> blockIp(UUID assetId, String confirmHostname, String ipAddress, String reason,
                                         String token) {
        return exchange(HttpMethod.POST, "/api/v1/assets/" + assetId + "/block-ip", token,
                Map.of("confirmHostname", confirmHostname, "ipAddress", ipAddress, "reason", reason), Void.class);
    }

    private <T> ResponseEntity<T> blockIp(UUID assetId, String confirmHostname, String ipAddress, String reason,
                                          String token, Class<T> type) {
        return exchange(HttpMethod.POST, "/api/v1/assets/" + assetId + "/block-ip", token,
                Map.of("confirmHostname", confirmHostname, "ipAddress", ipAddress, "reason", reason), type);
    }

    private static String suffix() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private String adminToken() {
        return login("admin", "IntegrationTest123!");
    }

    private String login(String username, String password) {
        return rest.postForEntity("/api/v1/auth/login",
                        Map.of("username", username, "password", password), TokenResponse.class)
                .getBody().accessToken();
    }

    private <T> ResponseEntity<T> exchange(HttpMethod method, String url, String token, Object body, Class<T> type) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        if (body != null) {
            headers.setContentType(MediaType.APPLICATION_JSON);
        }
        return rest.exchange(url, method, new HttpEntity<>(body, headers), type);
    }
}
