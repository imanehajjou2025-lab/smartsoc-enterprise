package com.smartsoc.domain.assets;

import com.smartsoc.domain.common.BusinessRuleViolationException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AssetTest {

    private static Asset sample() {
        return Asset.register(Asset.RegistrationData.builder()
                .hostname("srv-web-01")
                .displayName("Serveur web principal")
                .type(AssetType.SERVER)
                .criticality(AssetCriticality.CRITICAL)
                .exposure(AssetExposure.INTERNET_FACING)
                .ipAddress("10.10.1.20")
                .owner("Équipe infra")
                .build());
    }

    @Test
    void registerStartsActiveWithNormalizedHostname() {
        Asset asset = sample();

        assertThat(asset.getId()).isNotNull();
        assertThat(asset.getHostname()).isEqualTo("srv-web-01");
        assertThat(asset.getStatus()).isEqualTo(AssetStatus.ACTIVE);
        assertThat(asset.getDecommissionedAt()).isNull();
        assertThat(asset.getRegisteredAt()).isNotNull();
    }

    @Test
    void hostnameIsNormalizedCaseAndSpaces() {
        // La clé de corrélation : majuscules + espaces → minuscules, trim.
        Asset asset = Asset.register(Asset.RegistrationData.builder()
                .hostname("  SRV-WEB-01  ")
                .type(AssetType.SERVER)
                .criticality(AssetCriticality.HIGH)
                .exposure(AssetExposure.INTERNAL)
                .build());

        assertThat(asset.getHostname()).isEqualTo("srv-web-01");
        assertThat(Asset.normalizeHostname("  SRV-Web-01.CORP.Local "))
                .isEqualTo("srv-web-01.corp.local");

        assertThatThrownBy(() -> Asset.normalizeHostname("  "))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("hostname");
    }

    @Test
    void hostnameIsImmutable() {
        // Aucun mutateur n'expose le hostname : c'est l'identité de l'actif.
        // Le champ est final — la garantie est structurelle, ce test fige
        // le contrat public (updateDetails/reclassify n'y touchent pas).
        Asset asset = sample();
        asset.updateDetails("Nouveau nom", "10.10.1.99", "Autre équipe", "desc");
        asset.reclassify(AssetType.APPLICATION, AssetCriticality.LOW, AssetExposure.ISOLATED);

        assertThat(asset.getHostname()).isEqualTo("srv-web-01");
    }

    @Test
    void registerRejectsMissingClassification() {
        assertThatThrownBy(() -> Asset.register(Asset.RegistrationData.builder()
                .hostname("srv-x").criticality(AssetCriticality.LOW)
                .exposure(AssetExposure.INTERNAL).build()))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("type");
        assertThatThrownBy(() -> Asset.register(Asset.RegistrationData.builder()
                .hostname("srv-x").type(AssetType.SERVER)
                .exposure(AssetExposure.INTERNAL).build()))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("criticality");
        assertThatThrownBy(() -> Asset.register(Asset.RegistrationData.builder()
                .hostname("srv-x").type(AssetType.SERVER)
                .criticality(AssetCriticality.LOW).build()))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("exposure");
    }

    @Test
    void displayNameDefaultsToHostname() {
        Asset asset = Asset.register(Asset.RegistrationData.builder()
                .hostname("SRV-DB-01")
                .type(AssetType.DATABASE)
                .criticality(AssetCriticality.CRITICAL)
                .exposure(AssetExposure.INTERNAL)
                .build());

        assertThat(asset.getDisplayName()).isEqualTo("srv-db-01");
    }

    @Test
    void decommissionedAssetIsReadOnlyUntilReactivated() {
        Asset asset = sample();
        asset.decommission();
        assertThat(asset.getStatus()).isEqualTo(AssetStatus.DECOMMISSIONED);
        assertThat(asset.getDecommissionedAt()).isNotNull();

        assertThatThrownBy(() -> asset.updateDetails("nom", null, null, null))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("read-only");
        assertThatThrownBy(() -> asset.reclassify(AssetType.SERVER,
                AssetCriticality.LOW, AssetExposure.INTERNAL))
                .isInstanceOf(BusinessRuleViolationException.class);
        assertThatThrownBy(asset::decommission)
                .isInstanceOf(BusinessRuleViolationException.class);

        asset.reactivate();
        assertThat(asset.getStatus()).isEqualTo(AssetStatus.ACTIVE);
        assertThat(asset.getDecommissionedAt()).isNull();
        asset.updateDetails("De retour en service", null, null, null);
        assertThat(asset.getDisplayName()).isEqualTo("De retour en service");

        assertThatThrownBy(asset::reactivate)
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("decommissioned");
    }

    @Test
    void updateDetailsNormalizesBlanksAndKeepsDisplayName() {
        Asset asset = sample();

        asset.updateDetails("  ", "  ", " ", null);

        // Nom d'affichage conservé si blanc ; IP et propriétaire effacés.
        assertThat(asset.getDisplayName()).isEqualTo("Serveur web principal");
        assertThat(asset.getIpAddress()).isNull();
        assertThat(asset.getOwner()).isNull();
    }

    @Test
    void queryDefaultsToFirstPageWhenPageIsNull() {
        AssetQuery query = new AssetQuery(null, null, null, null, null, null);

        assertThat(query.page()).isNotNull();
        assertThat(query.page().page()).isZero();
        assertThat(query.page().size()).isEqualTo(25);
    }
}
