package com.smartsoc.domain.audit;

import com.smartsoc.domain.common.PageQuery;

import java.time.Instant;

/**
 * Recherche filtrée du journal d'audit, triée du plus récent au plus
 * ancien. Tous les filtres sont optionnels (null = pas de contrainte).
 *
 * @param targetType additif (ADR-014 phase 5) : permet à {@code SocActionService}
 *                   de compter les actions réelles récentes sur UNE cible
 *                   précise (plafond horaire), jamais requis ailleurs.
 */
public record AuditLogQuery(AuditAction action, String actorUsername, Instant from, Instant to,
                             String targetType, String targetId, PageQuery page) {
}
