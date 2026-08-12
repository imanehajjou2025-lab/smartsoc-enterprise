package com.smartsoc.domain.alerts;

import com.smartsoc.domain.common.BusinessRuleViolationException;
import com.smartsoc.domain.intelligence.Observable;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.Collections;
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
    /**
     * Observables cités par l'événement — la clé de corrélation avec le
     * référentiel CTI. Déclarés par le producteur, jamais devinés à
     * partir de rawPayload (ADR-009) : une extraction par expressions
     * régulières fabriquerait de faux rattachements, et en SOC un faux
     * rattachement coûte plus cher qu'une absence. Liste vide par défaut,
     * donc les producteurs qui n'en déclarent pas sont inchangés.
     */
    private final List<Observable> observables;
    private final String rawPayload;
    private Double aiScore;
    private AiVerdict aiVerdict;
    /** Enrichissement complémentaire optionnel — voir {@link #applyAiEnrichment}. */
    private AiZone aiZone;
    private boolean aiHardOverride;
    private List<String> aiJustifications;
    private AnalystTier assignedTier;
    private String assignedToUsername;

    /** Données d'ingestion — parameter object du point d'entrée unique. */
    @Builder
    public record IngestionData(
            String source,
            String externalId,
            String title,
            String description,
            Severity severity,
            Instant detectedAt,
            String hostname,
            String ruleId,
            List<String> mitreTechniques,
            List<Observable> observables,
            String rawPayload) {
    }

    /** Point d'entrée unique de création : une alerte naît de l'ingestion. */
    public static Alert ingest(IngestionData data) {
        requireNonBlank(data.source(), "source");
        requireNonBlank(data.externalId(), "externalId");
        requireNonBlank(data.title(), "title");
        if (data.severity() == null) {
            throw new BusinessRuleViolationException("INVALID_ALERT", "An alert must have a severity");
        }
        if (data.detectedAt() == null) {
            throw new BusinessRuleViolationException("INVALID_ALERT", "An alert must have a detection time");
        }
        return Alert.builder()
                .id(UUID.randomUUID())
                .source(data.source().trim().toLowerCase())
                .externalId(data.externalId().trim())
                .title(data.title().trim())
                .description(data.description())
                .severity(data.severity())
                .status(AlertStatus.NEW)
                .detectedAt(data.detectedAt())
                .receivedAt(Instant.now())
                .hostname(data.hostname())
                .ruleId(data.ruleId())
                .mitreTechniques(data.mitreTechniques() == null
                        ? List.of() : List.copyOf(data.mitreTechniques()))
                .observables(data.observables() == null
                        ? List.of() : List.copyOf(data.observables()))
                .rawPayload(data.rawPayload())
                .build();
    }

    /**
     * Observables cités, en lecture seule.
     *
     * <p>Accesseur écrit à la main plutôt que généré : {@code ingest()}
     * construit déjà une liste immuable, mais ce n'est pas le seul chemin
     * de création — une alerte relue depuis la base passe par le builder,
     * avec une liste ordinaire. L'entité rendrait alors modifiable ce
     * qu'elle est censée protéger. Une alerte est une pièce d'evidence :
     * ce qu'elle cite ne se réécrit pas après coup, quel que soit le
     * chemin par lequel elle a été obtenue.
     */
    public List<Observable> getObservables() {
        return readOnly(observables);
    }

    /**
     * Techniques ATT&CK, en lecture seule.
     *
     * <p>Exactement la même protection, pour exactement la même raison :
     * ce champ est le jumeau structurel d'{@code observables}. CodeQL n'a
     * signalé que le second parce qu'il ne remonte que les alertes du
     * code NOUVEAU d'une pull request — l'ancienneté d'un défaut ne le
     * rend pas moins réel. Deux collections de la même entité n'ont
     * aucune raison d'offrir des garanties différentes.
     */
    public List<String> getMitreTechniques() {
        return readOnly(mitreTechniques);
    }

    /**
     * Justifications explicables du classifieur, en lecture seule — même
     * protection que {@code observables}/{@code mitreTechniques}, pour la
     * même raison structurelle (CodeQL java/internal-representation-exposure).
     */
    public List<String> getAiJustifications() {
        return readOnly(aiJustifications);
    }

    /**
     * Vue non modifiable, tolérante au null : un chemin de construction
     * inhabituel ne doit pas se transformer en NullPointerException
     * différé chez l'appelant.
     */
    private static <T> List<T> readOnly(List<T> values) {
        return values == null ? List.of() : Collections.unmodifiableList(values);
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

    /**
     * Enrichissement COMPLÉMENTAIRE optionnel d'un classifieur qui va
     * au-delà du verdict binaire (zone de routage recommandée, dérogation
     * forcée par une preuve critique, justifications explicables) —
     * additif, ne remplace jamais {@link #applyAiAssessment}. Un
     * classifieur qui ne fournit pas cet enrichissement laisse
     * simplement ces champs à {@code null}/vide.
     */
    public void applyAiEnrichment(AiZone zone, boolean hardOverride, List<String> justifications) {
        this.aiZone = zone;
        this.aiHardOverride = hardOverride;
        this.aiJustifications = justifications == null ? List.of() : List.copyOf(justifications);
    }

    /** Affectation de triage (N1/N2/N3), avec analyste nommé optionnel — distincte de l'escalade en incident. */
    public void assignToTier(AnalystTier tier, String username) {
        if (tier == null) {
            throw new BusinessRuleViolationException("INVALID_ALERT_ASSIGNMENT",
                    "An assignment must specify a tier");
        }
        this.assignedTier = tier;
        this.assignedToUsername = (username == null || username.isBlank())
                ? null : username.trim().toLowerCase();
    }

    public void unassign() {
        this.assignedTier = null;
        this.assignedToUsername = null;
    }

    private static void requireNonBlank(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new BusinessRuleViolationException("INVALID_ALERT",
                    "Field '%s' must not be blank".formatted(field));
        }
    }
}
