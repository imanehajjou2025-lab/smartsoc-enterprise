package com.smartsoc.domain.alerts;

import com.smartsoc.domain.common.PageQuery;

/**
 * Critères de recherche d'alertes. Tout filtre null est ignoré ;
 * le tri est fixe : détection la plus récente d'abord.
 */
public record AlertQuery(
        AlertStatus status,
        Severity severity,
        String source,
        PageQuery page) {

    public AlertQuery {
        if (page == null) {
            page = PageQuery.of(0, 25);
        }
    }
}
