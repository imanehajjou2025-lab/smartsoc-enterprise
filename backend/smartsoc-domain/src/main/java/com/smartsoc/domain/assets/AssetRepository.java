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

    PageResult<Asset> search(AssetQuery query);
}
