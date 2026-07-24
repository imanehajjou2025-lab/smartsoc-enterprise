package com.smartsoc.application.assets;

import com.smartsoc.application.assets.AssetService.RegisterAssetCommand;
import com.smartsoc.application.assets.AssetService.UpdateAssetCommand;
import com.smartsoc.domain.alerts.Alert;
import com.smartsoc.domain.alerts.AlertRepository;
import com.smartsoc.domain.alerts.Severity;
import com.smartsoc.domain.assets.Asset;
import com.smartsoc.domain.assets.AssetCriticality;
import com.smartsoc.domain.assets.AssetExposure;
import com.smartsoc.domain.assets.AssetType;
import com.smartsoc.domain.common.BusinessRuleViolationException;
import com.smartsoc.domain.common.DuplicateResourceException;
import com.smartsoc.domain.common.PageQuery;
import com.smartsoc.domain.common.PageResult;
import com.smartsoc.domain.common.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Orchestration de l'inventaire : unicité du hostname (y compris sous
 * course), corrélation d'alertes par hostname normalisé — l'actif
 * transmet SA clé normalisée au port, quelle que soit la casse saisie —
 * et lecture de corrélation préservée sur un actif décommissionné.
 */
@ExtendWith(MockitoExtension.class)
class AssetServiceTest {

    @Mock
    private com.smartsoc.domain.assets.AssetRepository assetRepository;

    @Mock
    private AlertRepository alertRepository;

    @InjectMocks
    private AssetService service;

    private static Asset asset(String hostname) {
        return Asset.register(Asset.RegistrationData.builder()
                .hostname(hostname)
                .type(AssetType.SERVER)
                .criticality(AssetCriticality.CRITICAL)
                .exposure(AssetExposure.INTERNET_FACING)
                .build());
    }

    private static Alert alertWithRawHostname(String rawHostname) {
        return Alert.ingest(Alert.IngestionData.builder()
                .source("wazuh")
                .externalId("evt-" + UUID.randomUUID())
                .title("Brute force")
                .severity(Severity.HIGH)
                .detectedAt(Instant.now())
                .hostname(rawHostname)
                .build());
    }

    @Test
    void registerRejectsDuplicateHostnameEvenWithDifferentCase() {
        Asset existing = asset("srv-web-01");
        when(assetRepository.findByHostname("srv-web-01")).thenReturn(Optional.of(existing));

        RegisterAssetCommand duplicate = new RegisterAssetCommand(
                "  SRV-WEB-01 ", null, AssetType.SERVER, AssetCriticality.HIGH,
                AssetExposure.INTERNAL, null, null, null);
        assertThatThrownBy(() -> service.register(duplicate))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("srv-web-01")
                .extracting("code").isEqualTo("ASSET_ALREADY_EXISTS");
        verify(assetRepository, never()).save(any());
    }

    @Test
    void correlationQueriesTheNormalizedAssetHostname() {
        // L'actif enregistré en MAJUSCULES + espaces ; l'alerte au hostname
        // brut « SRV-WEB-01  » de la source. Le service doit interroger le
        // port avec la clé NORMALISÉE de l'actif — la normalisation SQL de
        // l'autre côté est prouvée par le test d'intégration du lot 6.
        Asset registered = asset("  SRV-WEB-01  ");
        assertThat(registered.getHostname()).isEqualTo("srv-web-01");
        Alert alert = alertWithRawHostname("SRV-WEB-01  ");
        when(assetRepository.findById(registered.getId()))
                .thenReturn(Optional.of(registered));
        when(alertRepository.findByNormalizedHostname("srv-web-01", PageQuery.of(0, 25)))
                .thenReturn(new PageResult<>(List.of(alert), 1, 0, 25));

        PageResult<Alert> correlated =
                service.getCorrelatedAlerts(registered.getId(), PageQuery.of(0, 25));

        assertThat(correlated.totalElements()).isEqualTo(1);
        assertThat(correlated.items().get(0).getHostname()).isEqualTo("SRV-WEB-01  ");
        verify(alertRepository).findByNormalizedHostname("srv-web-01", PageQuery.of(0, 25));
    }

    @Test
    void correlationWithNoAlertsReturnsAnEmptyPage() {
        Asset registered = asset("srv-neuf-01");
        when(assetRepository.findById(registered.getId()))
                .thenReturn(Optional.of(registered));
        when(alertRepository.findByNormalizedHostname("srv-neuf-01", PageQuery.of(0, 25)))
                .thenReturn(new PageResult<>(List.of(), 0, 0, 25));

        PageResult<Alert> correlated =
                service.getCorrelatedAlerts(registered.getId(), PageQuery.of(0, 25));

        assertThat(correlated.items()).isEmpty();
        assertThat(correlated.totalElements()).isZero();
    }

    @Test
    void decommissionedAssetKeepsItsCorrelatedAlerts() {
        Asset registered = asset("srv-legacy-01");
        registered.decommission();
        when(assetRepository.findById(registered.getId()))
                .thenReturn(Optional.of(registered));
        when(alertRepository.findByNormalizedHostname("srv-legacy-01", PageQuery.of(0, 25)))
                .thenReturn(new PageResult<>(List.of(alertWithRawHostname("srv-legacy-01")), 1, 0, 25));

        PageResult<Alert> correlated =
                service.getCorrelatedAlerts(registered.getId(), PageQuery.of(0, 25));

        // Lecture seule préservée : l'historique de corrélation reste accessible.
        assertThat(correlated.totalElements()).isEqualTo(1);
        // Les mutations, elles, restent bloquées par le domaine.
        UUID registeredId = registered.getId();
        UpdateAssetCommand update = new UpdateAssetCommand(
                "nom", null, null, null, AssetType.SERVER,
                AssetCriticality.LOW, AssetExposure.INTERNAL);
        assertThatThrownBy(() -> service.update(registeredId, update))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("read-only");
    }

    @Test
    void unknownAssetIs404() {
        UUID unknown = UUID.randomUUID();
        when(assetRepository.findById(unknown)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getAsset(unknown))
                .isInstanceOf(ResourceNotFoundException.class);
        PageQuery firstPage = PageQuery.of(0, 25);
        assertThatThrownBy(() -> service.getCorrelatedAlerts(unknown, firstPage))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void updateAppliesDetailsAndReclassification() {
        Asset registered = asset("srv-app-01");
        when(assetRepository.findById(registered.getId()))
                .thenReturn(Optional.of(registered));
        when(assetRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Asset updated = service.update(registered.getId(), new UpdateAssetCommand(
                "Application RH", "10.10.2.5", "Équipe RH", "SIRH",
                AssetType.APPLICATION, AssetCriticality.MEDIUM, AssetExposure.INTERNAL));

        assertThat(updated.getDisplayName()).isEqualTo("Application RH");
        assertThat(updated.getType()).isEqualTo(AssetType.APPLICATION);
        assertThat(updated.getCriticality()).isEqualTo(AssetCriticality.MEDIUM);
        assertThat(updated.getHostname()).isEqualTo("srv-app-01");
    }
}
