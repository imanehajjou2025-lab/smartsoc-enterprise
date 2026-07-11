package com.smartsoc.application.alerts;

import com.smartsoc.domain.alerts.Alert;

/**
 * Événement applicatif émis à chaque NOUVELLE alerte ingérée (pas au
 * replay). Découple l'ingestion de ses effets de bord : le temps réel
 * WebSocket aujourd'hui, le scoring IA et les règles SOAR demain
 * s'abonneront au même événement sans toucher à l'ingestion.
 */
public record AlertIngestedEvent(Alert alert) {
}
