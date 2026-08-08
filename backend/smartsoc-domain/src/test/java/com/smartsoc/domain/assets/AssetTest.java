package com.smartsoc.domain.assets;

import com.smartsoc.domain.common.BusinessRuleViolationException;
import org.junit.jupiter.api.Test;

import java.time.Instant;

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
        Asset.RegistrationData noType = Asset.RegistrationData.builder()
                .hostname("srv-x").criticality(AssetCriticality.LOW)
                .exposure(AssetExposure.INTERNAL).build();
        assertThatThrownBy(() -> Asset.register(noType))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("type");

        Asset.RegistrationData noCriticality = Asset.RegistrationData.builder()
                .hostname("srv-x").type(AssetType.SERVER)
                .exposure(AssetExposure.INTERNAL).build();
        assertThatThrownBy(() -> Asset.register(noCriticality))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("criticality");

        Asset.RegistrationData noExposure = Asset.RegistrationData.builder()
                .hostname("srv-x").type(AssetType.SERVER)
                .criticality(AssetCriticality.LOW).build();
        assertThatThrownBy(() -> Asset.register(noExposure))
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

    @Test
    void applySyncMetadataIsPurelyAdditive() {
        // Un actif enregistré à la main ne porte aucune métadonnée de
        // connecteur tant qu'aucune synchronisation ne l'a touché.
        Asset asset = sample();
        assertThat(asset.getExternalId()).isNull();
        assertThat(asset.getOperatingSystem()).isNull();
        assertThat(asset.getLastSeenAt()).isNull();

        Instant seenAt = Instant.now();
        asset.applySyncMetadata("004", "wazuh", "Ubuntu 24.04.4 LTS", seenAt);

        assertThat(asset.getExternalId()).isEqualTo("004");
        assertThat(asset.getExternalSource()).isEqualTo("wazuh");
        assertThat(asset.getOperatingSystem()).isEqualTo("Ubuntu 24.04.4 LTS");
        assertThat(asset.getLastSeenAt()).isEqualTo(seenAt);
        // Les champs métier existants n'ont pas bougé — c'est le point.
        assertThat(asset.getCriticality()).isEqualTo(AssetCriticality.CRITICAL);
        assertThat(asset.getOwner()).isEqualTo("Équipe infra");
    }

    @Test
    void applySyncMetadataBlanksAreCleared() {
        Asset asset = sample();
        asset.applySyncMetadata("004", "wazuh", "Ubuntu", Instant.now());

        asset.applySyncMetadata("  ", " ", null, null);

        assertThat(asset.getExternalId()).isNull();
        assertThat(asset.getExternalSource()).isNull();
        assertThat(asset.getOperatingSystem()).isNull();
        // lastSeenAt n'est jamais effacé par un null : c'est la dernière
        // observation connue, une absence de nouvelle donnée ne doit pas
        // effacer la précédente.
        assertThat(asset.getLastSeenAt()).isNotNull();
    }

    @Test
    void applySyncMetadataRejectedOnDecommissionedAsset() {
        Asset asset = sample();
        asset.decommission();

        assertThatThrownBy(() -> asset.applySyncMetadata("004", "wazuh", "Ubuntu", Instant.now()))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("decommissioned");
    }
}
