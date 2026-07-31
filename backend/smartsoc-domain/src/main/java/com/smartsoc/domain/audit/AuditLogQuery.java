package com.smartsoc.domain.audit;

import com.smartsoc.domain.common.PageQuery;

import java.time.Instant;

/**
 * Recherche filtrée du journal d'audit, triée du plus récent au plus
 * ancien. Tous les filtres sont optionnels (null = pas de contrainte).
 */
public record AuditLogQuery(AuditAction action, String actorUsername, Instant from, Instant to,
                             PageQuery page) {
}
