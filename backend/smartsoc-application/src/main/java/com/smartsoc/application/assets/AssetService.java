package com.smartsoc.application.assets;

import com.smartsoc.domain.alerts.Alert;
import com.smartsoc.domain.alerts.AlertRepository;
import com.smartsoc.domain.assets.Asset;
import com.smartsoc.domain.assets.AssetCriticality;
import com.smartsoc.domain.assets.AssetExposure;
import com.smartsoc.domain.assets.AssetQuery;
import com.smartsoc.domain.assets.AssetRepository;
import com.smartsoc.domain.assets.AssetType;
import com.smartsoc.domain.common.DuplicateResourceException;
import com.smartsoc.domain.common.PageQuery;
import com.smartsoc.domain.common.PageResult;
import com.smartsoc.domain.common.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Cas d'usage de l'inventaire des actifs. Pas de timeline propre :
 * l'audit passe par les colonnes created/updated_by (JPA auditing).
 * La corrélation d'alertes reste disponible sur un actif DÉCOMMISSIONNÉ
 * (lecture seule, historique préservé) — seules les mutations sont
 * bloquées, par le domaine.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AssetService {

    private final AssetRepository assetRepository;
    private final AlertRepository alertRepository;

    public record RegisterAssetCommand(
            String hostname,
            String displayName,
            AssetType type,
            AssetCriticality criticality,
            AssetExposure exposure,
            String ipAddress,
            String owner,
            String description) {
    }

    public record UpdateAssetCommand(
            String displayName,
            String ipAddress,
            String owner,
            String description,
            AssetType type,
            AssetCriticality criticality,
            AssetExposure exposure) {
    }

    @Transactional
    public Asset register(RegisterAssetCommand command) {
        String hostname = Asset.normalizeHostname(command.hostname());
        assetRepository.findByHostname(hostname).ifPresent(existing -> {
            throw new DuplicateResourceException("ASSET_ALREADY_EXISTS",
                    "An asset already exists for hostname '%s'".formatted(hostname));
        });
        Asset asset = Asset.register(Asset.RegistrationData.builder()
                .hostname(command.hostname())
                .displayName(command.displayName())
                .type(command.type())
                .criticality(command.criticality())
                .exposure(command.exposure())
                .ipAddress(command.ipAddress())
                .owner(command.owner())
                .description(command.description())
                .build());
        try {
            Asset saved = assetRepository.save(asset);
            log.info("Asset {} registered", saved.getHostname());
            return saved;
        } catch (DataIntegrityViolationException e) {
            // Course perdue contre un enregistrement concurrent du même hostname.
            throw new DuplicateResourceException("ASSET_ALREADY_EXISTS",
                    "An asset already exists for hostname '%s'".formatted(hostname));
        }
    }

    @Transactional
    public Asset update(UUID id, UpdateAssetCommand command) {
        Asset asset = requireAsset(id);
        asset.updateDetails(command.displayName(), command.ipAddress(),
                command.owner(), command.description());
        asset.reclassify(command.type(), command.criticality(), command.exposure());
        return assetRepository.save(asset);
    }

    @Transactional
    public Asset decommission(UUID id) {
        Asset asset = requireAsset(id);
        asset.decommission();
        Asset saved = assetRepository.save(asset);
        log.info("Asset {} decommissioned", saved.getHostname());
        return saved;
    }

    @Transactional
    public Asset reactivate(UUID id) {
        Asset asset = requireAsset(id);
        asset.reactivate();
        Asset saved = assetRepository.save(asset);
        log.info("Asset {} reactivated", saved.getHostname());
        return saved;
    }

    @Transactional(readOnly = true)
    public PageResult<Asset> search(AssetQuery query) {
        return assetRepository.search(query);
    }

    @Transactional(readOnly = true)
    public Asset getAsset(UUID id) {
        return requireAsset(id);
    }

    /**
     * Résolution par clé de corrélation : le client envoie la valeur
     * telle qu'il la connaît (ex. hostname brut d'une alerte), la
     * normalisation appartient au serveur. 404 = « aucun actif
     * inventorié pour ce hostname » — une information métier.
     */
    @Transactional(readOnly = true)
    public Asset getByHostname(String rawHostname) {
        String hostname = Asset.normalizeHostname(rawHostname);
        return assetRepository.findByHostname(hostname)
                .orElseThrow(() -> new ResourceNotFoundException("Asset", hostname));
    }

    /**
     * Alertes corrélées à l'actif : jointure normalisée côté SQL sur le
     * hostname (les alertes stockent la valeur brute de la source). Le
     * total de la page est LE compteur de corrélation — même prédicat.
     * Disponible aussi sur un actif décommissionné (historique).
     */
    @Transactional(readOnly = true)
    public PageResult<Alert> getCorrelatedAlerts(UUID id, PageQuery page) {
        Asset asset = requireAsset(id);
        return alertRepository.findByNormalizedHostname(asset.getHostname(), page);
    }

    private Asset requireAsset(UUID id) {
        return assetRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Asset", id));
    }
}
