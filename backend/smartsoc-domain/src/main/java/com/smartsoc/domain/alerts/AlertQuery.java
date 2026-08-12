package com.smartsoc.domain.alerts;

import com.smartsoc.domain.common.PageQuery;

import java.time.Instant;

/**
 * Critères de recherche d'alertes : statut, sévérité, source, hostname,
 * fenêtre de détection [from, to[ et niveau de triage affecté. Tout filtre
 * null est ignoré ; le tri est fixe : détection la plus récente d'abord.
 */
public record AlertQuery(
        AlertStatus status,
        Severity severity,
        String source,
        String hostname,
        Instant from,
        Instant to,
        AnalystTier assignedTier,
        PageQuery page) {

    public AlertQuery {
        if (page == null) {
            page = PageQuery.of(0, 25);
        }
    }
}
