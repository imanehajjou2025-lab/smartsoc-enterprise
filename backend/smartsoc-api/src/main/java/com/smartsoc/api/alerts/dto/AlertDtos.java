package com.smartsoc.api.alerts.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.smartsoc.domain.alerts.AiVerdict;
import com.smartsoc.domain.alerts.AlertStatus;
import com.smartsoc.domain.alerts.Severity;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Contrats REST du contexte alerts. */
public final class AlertDtos {

    private AlertDtos() {
    }

    /**
     * Payload du webhook d'ingestion — LE contrat des outils SOC
     * (spécification détaillée : docs/integration/alert-ingestion.md).
     * rawPayload accepte n'importe quel objet JSON : l'événement brut
     * intégral de l'outil source, conservé tel quel.
     */
    public record IngestAlertRequest(
            @NotBlank @Size(max = 50) String source,
            @NotBlank @Size(max = 255) String externalId,
            @NotBlank @Size(max = 500) String title,
            String description,
            @NotNull Severity severity,
            @NotNull Instant detectedAt,
            @Size(max = 255) String hostname,
            @Size(max = 100) String ruleId,
            List<@NotBlank String> mitreTechniques,
            JsonNode rawPayload) {
    }

    /** Demande de transition de triage (PATCH /alerts/{id}/status). */
    public record UpdateAlertStatusRequest(@NotNull AlertStatus status) {
    }

    public record AlertResponse(
            UUID id,
            String source,
            String externalId,
            String title,
            String description,
            Severity severity,
            AlertStatus status,
            Instant detectedAt,
            Instant receivedAt,
            String hostname,
            String ruleId,
            List<String> mitreTechniques,
            Double aiScore,
            AiVerdict aiVerdict) {
    }
}
