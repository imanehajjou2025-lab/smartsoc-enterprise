package com.smartsoc.application.audit;

import java.util.UUID;

/**
 * Identité de l'auteur d'une action sensible, résolue par la couche API
 * (JWT + requête HTTP) et transmise en paramètre simple aux services
 * applicatifs — même patron que {@code generatedBy} sur
 * {@code ReportGenerationService.generate(...)}, jamais de dépendance
 * servlet dans cette couche (ADR-002).
 */
public record ActorContext(String username, UUID userId, String ipAddress) {
}
