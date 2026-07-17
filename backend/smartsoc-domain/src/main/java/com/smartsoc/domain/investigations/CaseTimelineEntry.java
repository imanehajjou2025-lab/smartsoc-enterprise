package com.smartsoc.domain.investigations;

import java.time.Instant;
import java.util.UUID;

/**
 * Entrée horodatée de la timeline d'un cas — la trace d'enquête globale
 * (création, statut, affectation, liaisons, tâches, notes, clôture,
 * ouverture de suivi). {@code author} est l'analyste à l'origine de
 * l'événement (ou "system").
 */
public record CaseTimelineEntry(
        UUID id,
        UUID caseId,
        CaseEventType type,
        String message,
        String author,
        Instant occurredAt) {

    public static CaseTimelineEntry of(UUID caseId, CaseEventType type,
                                       String message, String author) {
        return new CaseTimelineEntry(
                UUID.randomUUID(), caseId, type, message, author, Instant.now());
    }
}
