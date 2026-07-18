package com.smartsoc.domain.assets;

import com.smartsoc.domain.common.PageQuery;

/**
 * Critères de recherche d'actifs. Tout filtre null est ignoré ; `search`
 * cherche dans le hostname et le nom d'affichage (insensible à la casse).
 * Tri fixe : criticité la plus haute d'abord, puis hostname.
 */
public record AssetQuery(
        AssetType type,
        AssetCriticality criticality,
        AssetExposure exposure,
        AssetStatus status,
        String search,
        PageQuery page) {

    public AssetQuery {
        if (page == null) {
            page = PageQuery.of(0, 25);
        }
    }
}
