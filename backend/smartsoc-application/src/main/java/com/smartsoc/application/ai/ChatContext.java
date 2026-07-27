package com.smartsoc.application.ai;

import java.util.UUID;

/**
 * Ancrage métier optionnel d'une conversation. Miroir du schéma
 * ChatContext du contrat docs/integration/ai-assistant-api.yaml.
 * `summary` est préparé par l'appelant (le frontend, propriétaire de la
 * conversation) — le backend ne relit jamais l'alerte/l'incident pour le
 * reconstruire, il relaie tel quel.
 */
public record ChatContext(UUID alertId, UUID incidentId, String summary) {
}
