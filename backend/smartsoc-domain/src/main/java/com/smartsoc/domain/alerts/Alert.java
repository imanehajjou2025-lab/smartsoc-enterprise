package com.smartsoc.domain.alerts;

import com.smartsoc.domain.common.BusinessRuleViolationException;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Alerte de sécurité normalisée — le concept central de la plateforme.
 * Quelle que soit la source (Wazuh, Suricata, connecteur…), une alerte a la
 * même forme ici ; l'événement brut intégral est conservé dans rawPayload
 * pour l'investigation. Les champs IA sont nullables : la plateforme
 * fonctionne intégralement sans le classifieur externe (ADR-005).
 */
@Getter
@Builder(toBuilder = true)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Alert {

    private final UUID id;
    private final String source;
    private final String externalId;
    private final String title;
    private final String description;
    private final Severity severity;
    private AlertStatus status;
    private final Instant detectedAt;
    private final Instant receivedAt;
    private final String hostname;
    private final String ruleId;
    private final List<String> mitreTechniques;
    private final String rawPayload;
    private Double aiScore;
    private AiVerdict aiVerdict;

    /** Point d'entrée unique de création : une alerte naît de l'ingestion. */
    public static Alert ingest(String source, String externalId, String title,
                               String description, Severity severity, Instant detectedAt,
                               String hostname, String ruleId, List<String> mitreTechniques,
                               String rawPayload) {
        requireNonBlank(source, "source");
        requireNonBlank(externalId, "externalId");
        requireNonBlank(title, "title");
        if (severity == null) {
            throw new BusinessRuleViolationException("INVALID_ALERT", "An alert must have a severity");
        }
        if (detectedAt == null) {
            throw new BusinessRuleViolationException("INVALID_ALERT", "An alert must have a detection time");
        }
        return Alert.builder()
                .id(UUID.randomUUID())
                .source(source.trim().toLowerCase())
                .externalId(externalId.trim())
                .title(title.trim())
                .description(description)
                .severity(severity)
                .status(AlertStatus.NEW)
                .detectedAt(detectedAt)
                .receivedAt(Instant.now())
                .hostname(hostname)
                .ruleId(ruleId)
                .mitreTechniques(mitreTechniques == null ? List.of() : List.copyOf(mitreTechniques))
                .rawPayload(rawPayload)
                .build();
    }

    /** Transition de triage, gardée par le cycle de vie (voir AlertStatus). */
    public void transitionTo(AlertStatus newStatus) {
        if (newStatus == null || !status.allowedTransitions().contains(newStatus)) {
            throw new BusinessRuleViolationException("INVALID_ALERT_TRANSITION",
                    "Cannot transition alert from %s to %s".formatted(status, newStatus));
        }
        this.status = newStatus;
    }

    /** Réception du score du classifieur TP/FP externe (jalon intégration IA). */
    public void applyAiAssessment(double score, AiVerdict verdict) {
        if (score < 0.0 || score > 1.0) {
            throw new BusinessRuleViolationException("INVALID_AI_SCORE",
                    "AI score must be between 0 and 1");
        }
        if (verdict == null) {
            throw new BusinessRuleViolationException("INVALID_AI_SCORE",
                    "AI assessment requires a verdict");
        }
        this.aiScore = score;
        this.aiVerdict = verdict;
    }

    private static void requireNonBlank(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new BusinessRuleViolationException("INVALID_ALERT",
                    "Field '%s' must not be blank".formatted(field));
        }
    }
}
