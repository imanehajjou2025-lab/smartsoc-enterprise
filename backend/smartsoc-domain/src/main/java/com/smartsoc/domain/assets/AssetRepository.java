package com.smartsoc.domain.assets;

import com.smartsoc.domain.common.PageResult;

import java.util.Optional;
import java.util.UUID;

/** Outbound port for asset inventory persistence. */
public interface AssetRepository {

    Asset save(Asset asset);

    Optional<Asset> findById(UUID id);

    /** Recherche par clé de corrélation (hostname déjà normalisé). */
    Optional<Asset> findByHostname(String hostname);

    /**
     * Recherche par référence externe (ADR-014) — la clé de réconciliation
     * d'un connecteur, distincte du hostname : un même actif peut changer
     * de nom sans perdre son identité côté outil source.
     */
    Optional<Asset> findByExternalRef(String externalSource, String externalId);

    PageResult<Asset> search(AssetQuery query);
}
