package com.smartsoc.application.connectors;

import com.smartsoc.domain.assets.Asset;
import com.smartsoc.domain.assets.AssetCriticality;
import com.smartsoc.domain.assets.AssetExposure;
import com.smartsoc.domain.assets.AssetRepository;
import com.smartsoc.domain.assets.AssetType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Réconciliation d'UN agent Wazuh vers le module Actifs — SÉPARÉE
 * d'{@link AgentSyncService} pour la même raison que
 * {@code IndicatorFeedIngestionService} (module intelligence) :
 * <ul>
 *   <li>en PostgreSQL, une violation de contrainte (ex. hostname déjà
 *       pris par un actif d'une autre identité) rend la transaction
 *       courante irrécupérable — un agent fautif condamnerait tous les
 *       suivants s'ils partageaient sa transaction ;</li>
 *   <li>un appel interne à une méthode {@code @Transactional} du même
 *       bean court-circuite le proxy Spring. Passer par ce bean DÉDIÉ
 *       garantit que chaque agent est réellement isolé.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class AgentReconciliationService {

    static final String SOURCE = "wazuh";

    private final AssetRepository assetRepository;

    @Transactional
    public void reconcileOne(AgentInventoryPort.AgentSnapshot snapshot) {
        reconcileOne(snapshot, null);
    }

    /**
     * @param systemDetails détail syscollector, {@code null} si non récupéré
     *                       ce cycle (agent jamais scanné, ou sonde en échec —
     *                       voir {@link AgentSyncService}). Quand présent, sa
     *                       description d'OS est PLUS RICHE que celle de la
     *                       liste d'agents de base et prend le dessus.
     */
    @Transactional
    public void reconcileOne(AgentInventoryPort.AgentSnapshot snapshot,
                             SystemInventoryPort.SystemDetails systemDetails) {
        Asset asset = assetRepository.findByExternalRef(SOURCE, snapshot.externalId())
                .orElseGet(() -> registerFromAgent(snapshot));

        String operatingSystem = (systemDetails != null && systemDetails.operatingSystemDetail() != null)
                ? systemDetails.operatingSystemDetail()
                : snapshot.operatingSystem();
        String hardwareSummary = systemDetails == null ? null : systemDetails.hardwareSummary();

        asset.applySyncMetadata(snapshot.externalId(), SOURCE,
                operatingSystem, snapshot.lastSeenAt(), hardwareSummary);

        assetRepository.save(asset);
    }

    /**
     * Un agent nouvellement découvert n'apporte aucun jugement SOC
     * (criticité, exposition) : Wazuh ne le connaît pas. Défauts
     * DÉLIBÉRÉMENT neutres — {@code OTHER}/{@code MEDIUM}/{@code INTERNAL}
     * plutôt qu'une supposition — modifiables ensuite par un analyste via
     * {@link Asset#reclassify}, jamais réécrits par une synchronisation
     * suivante (qui ne touche que les champs additifs).
     */
    private static Asset registerFromAgent(AgentInventoryPort.AgentSnapshot snapshot) {
        return Asset.register(Asset.RegistrationData.builder()
                .hostname(snapshot.hostname())
                .type(AssetType.OTHER)
                .criticality(AssetCriticality.MEDIUM)
                .exposure(AssetExposure.INTERNAL)
                .ipAddress(snapshot.ipAddress())
                .description("Découvert automatiquement via le connecteur Wazuh (id agent %s)."
                        .formatted(snapshot.externalId()))
                .build());
    }
}
