package com.smartsoc.domain.incidents;

import java.time.Instant;
import java.util.UUID;

/**
 * Entrée horodatée de la timeline d'un incident — la trace d'investigation
 * (création, changement de statut, affectation, note, liaison d'alerte).
 * {@code author} est l'analyste à l'origine de l'événement (ou "system").
 */
public record IncidentTimelineEntry(
        UUID id,
        UUID incidentId,
        IncidentEventType type,
        String message,
        String author,
        Instant occurredAt) {

    public static IncidentTimelineEntry of(UUID incidentId, IncidentEventType type,
                                           String message, String author) {
        return new IncidentTimelineEntry(
                UUID.randomUUID(), incidentId, type, message, author, Instant.now());
    }
}
