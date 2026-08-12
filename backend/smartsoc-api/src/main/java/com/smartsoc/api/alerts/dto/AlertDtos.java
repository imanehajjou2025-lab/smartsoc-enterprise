package com.smartsoc.api.alerts.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import com.smartsoc.domain.alerts.AiVerdict;
import com.smartsoc.domain.alerts.AiZone;
import com.smartsoc.domain.alerts.AlertStatus;
import com.smartsoc.domain.alerts.AnalystTier;
import com.smartsoc.domain.alerts.Severity;
import com.smartsoc.domain.intelligence.IndicatorType;
import com.smartsoc.domain.intelligence.Observable;
import jakarta.validation.Valid;
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
            @Size(max = MAX_DECLARED_OBSERVABLES)
            List<@Valid @NotNull ObservableRequest> observables,
            JsonNode rawPayload) {
    }

    /**
     * Plafond ANTI-ABUS du tableau reçu, distinct de la borne métier
     * {@code Observable.MAX_PER_ALERT} et volontairement bien plus haut.
     *
     * <p>Les deux ne servent pas la même chose. La borne métier (100)
     * s'applique avec TOLÉRANCE : au-delà, le surplus est écarté et
     * nommé, l'alerte est conservée. Ce plafond-ci protège la mémoire
     * AVANT désérialisation — sans lui, un producteur pourrait envoyer
     * un million d'entrées que Jackson matérialiserait intégralement
     * juste pour que le domaine n'en garde que cent.
     *
     * <p>Le laisser large garantit que le dépassement ordinaire reste
     * traité en tolérance et ne fait jamais perdre une alerte ; seul un
     * payload manifestement abusif est refusé en 400.
     */
    public static final int MAX_DECLARED_OBSERVABLES = 1000;

    /**
     * Observable déclaré par le producteur. Champ ENTIÈREMENT OPTIONNEL
     * du payload : absent, {@code null} ou vide, l'ingestion se comporte
     * exactement comme avant son introduction.
     *
     * <p>La valeur peut arriver défangée ou en majuscules — la
     * normalisation appartient au serveur, et la réponse renvoie la forme
     * retenue.
     */
    public record ObservableRequest(
            @NotNull IndicatorType type,
            @NotBlank @Size(max = 2048) String value) {
    }

    /**
     * Un observable écarté. {@code code} est la valeur CONTRACTUELLE et
     * stable ({@code INVALID_SHA256}, {@code INVALID_DOMAIN},
     * {@code TOO_MANY_OBSERVABLES}…) : c'est sur elle qu'une intégration
     * se branche. {@code message} est informatif et peut évoluer sans
     * casser personne — ne l'analysez pas.
     *
     * <p>{@code index} est la position dans le tableau ENVOYÉ, pour
     * retrouver l'entrée fautive dans le mapping du producteur.
     */
    public record ObservableRejection(
            int index,
            IndicatorType type,
            String value,
            String code,
            String message) {
    }

    /**
     * Compte rendu de lecture des observables. Groupé plutôt qu'à plat
     * pour rester extensible : d'éventuels avertissements ou statistiques
     * s'y ajouteront sans toucher au reste de la réponse.
     */
    public record ObservableReport(
            int accepted,
            int rejected,
            List<ObservableRejection> errors) {
    }

    /** Demande de transition de triage (PATCH /alerts/{id}/status). */
    public record UpdateAlertStatusRequest(@NotNull AlertStatus status) {
    }

    /** Demande d'affectation de triage (PUT /alerts/{id}/assignment). Username optionnel : on peut affecter un niveau sans nommer d'analyste. */
    public record AssignAlertRequest(@NotNull AnalystTier tier, @Size(max = 50) String username) {
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
            /** Observables RETENUS, sous leur forme normalisée — celle
             *  qui servira effectivement à la corrélation. */
            List<Observable> observables,
            String rawPayload,
            Double aiScore,
            AiVerdict aiVerdict,
            /** Enrichissement complémentaire optionnel (ADR-008) — nullable/vide
             *  tant qu'aucune classification ne l'a fourni. */
            AiZone aiZone,
            boolean aiHardOverride,
            List<String> aiJustifications,
            /** Niveau de triage affecté (N1/N2/N3) — distinct de l'escalade en incident. */
            AnalystTier assignedTier,
            String assignedToUsername,
            /** Renseigné uniquement par le webhook d'ingestion : ailleurs
             *  il n'y a rien à rendre compte, donc le champ est absent
             *  du JSON plutôt que présent à null. */
            @JsonInclude(JsonInclude.Include.NON_NULL) ObservableReport observableReport) {

        /** Même alerte, avec son compte rendu d'ingestion. */
        public AlertResponse withObservableReport(ObservableReport report) {
            return new AlertResponse(id, source, externalId, title, description, severity,
                    status, detectedAt, receivedAt, hostname, ruleId, mitreTechniques,
                    observables, rawPayload, aiScore, aiVerdict, aiZone, aiHardOverride,
                    aiJustifications, assignedTier, assignedToUsername, report);
        }
    }
}
