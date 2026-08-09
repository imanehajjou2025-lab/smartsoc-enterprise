package com.smartsoc.application.connectors;

import com.smartsoc.domain.assets.Asset;
import com.smartsoc.domain.assets.AssetCriticality;
import com.smartsoc.domain.assets.AssetExposure;
import com.smartsoc.domain.assets.AssetRepository;
import com.smartsoc.domain.assets.AssetType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgentReconciliationServiceTest {

    @Mock
    private AssetRepository assetRepository;

    private AgentReconciliationService service;

    @Test
    void unknownAgentIsRegisteredWithNeutralDefaults() {
        service = new AgentReconciliationService(assetRepository);
        Instant seenAt = Instant.now();
        var snapshot = new AgentInventoryPort.AgentSnapshot(
                "004", "WIN10-CLIENT", "10.100.0.9", "Microsoft Windows 10 Home", seenAt);
        when(assetRepository.findByExternalRef("wazuh", "004")).thenReturn(Optional.empty());
        when(assetRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.reconcileOne(snapshot);

        ArgumentCaptor<Asset> captor = ArgumentCaptor.forClass(Asset.class);
        verify(assetRepository).save(captor.capture());
        Asset created = captor.getValue();
        assertThat(created.getHostname()).isEqualTo("win10-client");
        // Défauts neutres : Wazuh ne porte aucun jugement SOC.
        assertThat(created.getType()).isEqualTo(AssetType.OTHER);
        assertThat(created.getCriticality()).isEqualTo(AssetCriticality.MEDIUM);
        assertThat(created.getExternalId()).isEqualTo("004");
        assertThat(created.getExternalSource()).isEqualTo("wazuh");
        assertThat(created.getOperatingSystem()).isEqualTo("Microsoft Windows 10 Home");
        assertThat(created.getLastSeenAt()).isEqualTo(seenAt);
    }

    @Test
    void knownAgentIsUpdatedNotDuplicated() {
        service = new AgentReconciliationService(assetRepository);
        Asset existing = Asset.register(Asset.RegistrationData.builder()
                .hostname("win10-client")
                .type(AssetType.WORKSTATION)
                .criticality(AssetCriticality.HIGH)
                .exposure(AssetExposure.INTERNAL)
                .build());
        existing.applySyncMetadata("004", "wazuh", "old-os", Instant.now().minusSeconds(3600));

        when(assetRepository.findByExternalRef("wazuh", "004")).thenReturn(Optional.of(existing));
        when(assetRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Instant freshSeen = Instant.now();
        service.reconcileOne(new AgentInventoryPort.AgentSnapshot(
                "004", "WIN10-CLIENT", "10.100.0.9", "Windows 10 Pro", freshSeen));

        ArgumentCaptor<Asset> captor = ArgumentCaptor.forClass(Asset.class);
        verify(assetRepository).save(captor.capture());
        Asset updated = captor.getValue();
        // La MÊME identité d'actif — mise à jour, pas un doublon.
        assertThat(updated.getId()).isEqualTo(existing.getId());
        assertThat(updated.getOperatingSystem()).isEqualTo("Windows 10 Pro");
        assertThat(updated.getLastSeenAt()).isEqualTo(freshSeen);
        // Le jugement métier déjà porté par un analyste (HIGH) n'est PAS écrasé.
        assertThat(updated.getCriticality()).isEqualTo(AssetCriticality.HIGH);
    }

    @Test
    void systemDetailsOsDescriptionTakesPrecedenceOverTheBaseSnapshot() {
        service = new AgentReconciliationService(assetRepository);
        var snapshot = new AgentInventoryPort.AgentSnapshot(
                "004", "WIN10-CLIENT", "10.100.0.9", "Microsoft Windows 10 Home", Instant.now());
        var systemDetails = new SystemInventoryPort.SystemDetails(
                "Microsoft Windows 10 Home 22H2 (build 19045.3803)",
                "12th Gen Intel(R) Core(TM) i5-12450H, 1 coeur, 2 Go RAM");
        when(assetRepository.findByExternalRef("wazuh", "004")).thenReturn(Optional.empty());
        when(assetRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.reconcileOne(snapshot, systemDetails);

        ArgumentCaptor<Asset> captor = ArgumentCaptor.forClass(Asset.class);
        verify(assetRepository).save(captor.capture());
        Asset created = captor.getValue();
        // La description syscollector (plus riche) l'emporte sur celle,
        // plus pauvre, de la liste d'agents de base.
        assertThat(created.getOperatingSystem()).isEqualTo("Microsoft Windows 10 Home 22H2 (build 19045.3803)");
        assertThat(created.getHardwareSummary())
                .isEqualTo("12th Gen Intel(R) Core(TM) i5-12450H, 1 coeur, 2 Go RAM");
    }

    @Test
    void nullSystemDetailsFallsBackToTheBaseSnapshotOsAndLeavesHardwareUnset() {
        service = new AgentReconciliationService(assetRepository);
        var snapshot = new AgentInventoryPort.AgentSnapshot(
                "004", "WIN10-CLIENT", "10.100.0.9", "Microsoft Windows 10 Home", Instant.now());
        when(assetRepository.findByExternalRef("wazuh", "004")).thenReturn(Optional.empty());
        when(assetRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // systemDetails absent -- agent jamais scanne, ou sonde en echec ce cycle.
        service.reconcileOne(snapshot, null);

        ArgumentCaptor<Asset> captor = ArgumentCaptor.forClass(Asset.class);
        verify(assetRepository).save(captor.capture());
        Asset created = captor.getValue();
        assertThat(created.getOperatingSystem()).isEqualTo("Microsoft Windows 10 Home");
        assertThat(created.getHardwareSummary()).isNull();
    }

    @Test
    void systemDetailsHardwareOnlyStillFallsBackToBaseOsDescription() {
        service = new AgentReconciliationService(assetRepository);
        var snapshot = new AgentInventoryPort.AgentSnapshot(
                "004", "WIN10-CLIENT", "10.100.0.9", "Microsoft Windows 10 Home", Instant.now());
        // Le endpoint /os a echoue ce cycle mais /hardware a repondu :
        // operatingSystemDetail() est null, on retombe sur le snapshot de base.
        var systemDetails = new SystemInventoryPort.SystemDetails(null, "Intel Core i5, 1 coeur, 2 Go RAM");
        when(assetRepository.findByExternalRef("wazuh", "004")).thenReturn(Optional.empty());
        when(assetRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.reconcileOne(snapshot, systemDetails);

        ArgumentCaptor<Asset> captor = ArgumentCaptor.forClass(Asset.class);
        verify(assetRepository).save(captor.capture());
        Asset created = captor.getValue();
        assertThat(created.getOperatingSystem()).isEqualTo("Microsoft Windows 10 Home");
        assertThat(created.getHardwareSummary()).isEqualTo("Intel Core i5, 1 coeur, 2 Go RAM");
    }
}
