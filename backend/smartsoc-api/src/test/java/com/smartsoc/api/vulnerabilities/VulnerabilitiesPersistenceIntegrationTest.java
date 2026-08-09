package com.smartsoc.api.vulnerabilities;

import com.smartsoc.TestcontainersConfiguration;
import com.smartsoc.domain.assets.Asset;
import com.smartsoc.domain.assets.AssetCriticality;
import com.smartsoc.domain.assets.AssetExposure;
import com.smartsoc.domain.assets.AssetRepository;
import com.smartsoc.domain.assets.AssetType;
import com.smartsoc.domain.common.PageQuery;
import com.smartsoc.domain.vulnerabilities.Vulnerability;
import com.smartsoc.domain.vulnerabilities.VulnerabilityQuery;
import com.smartsoc.domain.vulnerabilities.VulnerabilityRepository;
import com.smartsoc.domain.vulnerabilities.VulnerabilitySeverity;
import com.smartsoc.domain.vulnerabilities.VulnerabilityStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Persistance du contexte vulnerabilities (V18, ADR-014 §1.3) sur un
 * vrai PostgreSQL : identité (source, externalId), rattachement à un
 * actif réel, réouverture d'une vulnérabilité résolue, recherche filtrée.
 */
@SpringBootTest(properties = "smartsoc.security.bootstrap-admin.password=IntegrationTest123!")
@Import(TestcontainersConfiguration.class)
class VulnerabilitiesPersistenceIntegrationTest {

    @Autowired
    private VulnerabilityRepository vulnerabilityRepository;

    @Autowired
    private AssetRepository assetRepository;

    private UUID registerAsset(String hostname) {
        return assetRepository.save(Asset.register(Asset.RegistrationData.builder()
                .hostname(hostname)
                .type(AssetType.SERVER)
                .criticality(AssetCriticality.MEDIUM)
                .exposure(AssetExposure.INTERNAL)
                .build())).getId();
    }

    private static Vulnerability.Finding.FindingBuilder finding(UUID assetId, String externalId) {
        return Vulnerability.Finding.builder()
                .assetId(assetId)
                .source("wazuh")
                .externalId(externalId)
                .cveId("CVE-2025-3576")
                .severity(VulnerabilitySeverity.MEDIUM)
                .cvssScore(5.9)
                .cvssVersion("3.1")
                .description("MIT Kerberos GSSAPI spoofing")
                .packageName("libgssapi-krb5-2")
                .packageVersion("1.20.1-6ubuntu2.6")
                .packageArchitecture("amd64")
                .detectedAt(Instant.parse("2026-07-17T10:55:56Z"))
                .publishedAt(Instant.parse("2025-04-15T06:15:44Z"))
                .reference("https://ubuntu.com/security/CVE-2025-3576")
                .observedAt(Instant.now());
    }

    @Test
    void savesAndReloadsAVulnerabilityByExternalRef() {
        UUID assetId = registerAsset("persist-vuln-1");
        String externalId = "003_hash1_" + UUID.randomUUID();
        vulnerabilityRepository.save(Vulnerability.detect(finding(assetId, externalId).build()));

        Vulnerability reloaded = vulnerabilityRepository.findByExternalRef("wazuh", externalId).orElseThrow();
        assertThat(reloaded.getAssetId()).isEqualTo(assetId);
        assertThat(reloaded.getCveId()).isEqualTo("CVE-2025-3576");
        assertThat(reloaded.getSeverity()).isEqualTo(VulnerabilitySeverity.MEDIUM);
        assertThat(reloaded.getStatus()).isEqualTo(VulnerabilityStatus.OPEN);
    }

    @Test
    void refreshingAndSavingTheSameInstanceUpdatesRatherThanDuplicates() {
        UUID assetId = registerAsset("persist-vuln-2");
        String externalId = "003_hash2_" + UUID.randomUUID();
        Vulnerability vuln = Vulnerability.detect(finding(assetId, externalId).build());
        vulnerabilityRepository.save(vuln);

        vuln.refreshFrom(finding(assetId, externalId).severity(VulnerabilitySeverity.HIGH).build());
        vulnerabilityRepository.save(vuln);

        Vulnerability reloaded = vulnerabilityRepository.findByExternalRef("wazuh", externalId).orElseThrow();
        assertThat(reloaded.getSeverity()).isEqualTo(VulnerabilitySeverity.HIGH);
        // Une seule ligne pour cette identité : le second save() a mis à jour, pas dupliqué.
        List<Vulnerability> open = vulnerabilityRepository.findByAssetIdAndStatus(assetId, VulnerabilityStatus.OPEN);
        assertThat(open).extracting(Vulnerability::getExternalId).containsExactly(externalId);
    }

    @Test
    void duplicateExternalRefIsRejectedByTheUniqueIndex() {
        UUID assetId = registerAsset("persist-vuln-3");
        String externalId = "003_hash3_" + UUID.randomUUID();
        vulnerabilityRepository.save(Vulnerability.detect(finding(assetId, externalId).build()));

        // DEUX instances distinctes (deux id) visant la MÊME identité
        // (source, externalId) : ux_vulnerabilities_external_ref doit
        // refuser le doublon, même patron que l'index de dédoublonnage des alertes.
        assertThatThrownBy(() ->
                vulnerabilityRepository.save(Vulnerability.detect(finding(assetId, externalId).build())))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void resolveThenReopenRoundTripsTheStatusAndClearsResolvedAt() {
        UUID assetId = registerAsset("persist-vuln-4");
        String externalId = "003_hash4_" + UUID.randomUUID();
        Vulnerability vuln = Vulnerability.detect(finding(assetId, externalId).build());
        vulnerabilityRepository.save(vuln);

        vuln.resolve(Instant.now());
        vulnerabilityRepository.save(vuln);
        Vulnerability resolved = vulnerabilityRepository.findByExternalRef("wazuh", externalId).orElseThrow();
        assertThat(resolved.getStatus()).isEqualTo(VulnerabilityStatus.RESOLVED);
        assertThat(resolved.getResolvedAt()).isNotNull();

        resolved.refreshFrom(finding(assetId, externalId).build());
        vulnerabilityRepository.save(resolved);
        Vulnerability reopened = vulnerabilityRepository.findByExternalRef("wazuh", externalId).orElseThrow();
        assertThat(reopened.getStatus()).isEqualTo(VulnerabilityStatus.OPEN);
        assertThat(reopened.getResolvedAt()).isNull();
    }

    @Test
    void searchFiltersBySeverityStatusAndAssetWithPagination() {
        UUID assetId = registerAsset("persist-vuln-5");
        vulnerabilityRepository.save(Vulnerability.detect(
                finding(assetId, "003_hashA_" + UUID.randomUUID()).severity(VulnerabilitySeverity.CRITICAL).build()));
        vulnerabilityRepository.save(Vulnerability.detect(
                finding(assetId, "003_hashB_" + UUID.randomUUID()).severity(VulnerabilitySeverity.LOW).build()));

        var result = vulnerabilityRepository.search(new VulnerabilityQuery(
                assetId, VulnerabilityStatus.OPEN, VulnerabilitySeverity.CRITICAL, null, PageQuery.of(0, 25)));

        assertThat(result.items()).hasSize(1);
        assertThat(result.items().get(0).getSeverity()).isEqualTo(VulnerabilitySeverity.CRITICAL);
    }

    @Test
    void searchTextMatchesCveIdOrPackageName() {
        UUID assetId = registerAsset("persist-vuln-6");
        vulnerabilityRepository.save(Vulnerability.detect(
                finding(assetId, "003_hashC_" + UUID.randomUUID()).build()));

        var result = vulnerabilityRepository.search(new VulnerabilityQuery(
                null, null, null, "krb5", PageQuery.of(0, 25)));

        assertThat(result.items()).isNotEmpty();
        assertThat(result.items()).allSatisfy(v -> assertThat(v.getPackageName()).containsIgnoringCase("krb5"));
    }
}
