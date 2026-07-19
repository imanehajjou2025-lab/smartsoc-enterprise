package com.smartsoc.domain.intelligence;

import com.smartsoc.domain.common.BusinessRuleViolationException;
import com.smartsoc.domain.common.TextNormalization;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Indicateur de compromission (IOC) — une adresse, un domaine, une URL,
 * un hash ou une adresse e-mail dont on sait qu'il est associé à une
 * activité malveillante.
 *
 * <p><b>Deux natures strictement séparées dans cette entité :</b>
 * <ul>
 *   <li>l'<b>IDENTITÉ MÉTIER</b> — le couple (type, valeur normalisée).
 *       Elle est finale : c'est la clé de corrélation avec les
 *       observables des alertes, exactement le rôle que joue le hostname
 *       pour un actif. Un upsert de flux ne la touche JAMAIS ; prétendre
 *       modifier l'identité, c'est parler d'un autre indicateur ;</li>
 *   <li>les <b>MÉTADONNÉES CTI</b> — confiance, source, tags, fenêtre de
 *       validité, dates d'observation. Elles sont volatiles par nature :
 *       chaque passage du flux les rafraîchit.</li>
 * </ul>
 *
 * <p>Pas de suppression : un IOC qui ne vaut plus rien expire (fenêtre de
 * validité dépassée) ou se révoque (décision d'analyste), et reste
 * consultable — la même doctrine que l'actif décommissionné.
 *
 * <p>La provenance reste externe à la plateforme (ADR-005) : les flux
 * CTI poussent leurs indicateurs, SmartSOC n'interroge aucun MISP.
 */
@Getter
@Builder(toBuilder = true)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Indicator {

    private static final String INVALID = "INVALID_INDICATOR";
    private static final int MIN_CONFIDENCE = 0;
    private static final int MAX_CONFIDENCE = 100;

    // ---------- IDENTITÉ MÉTIER (immuable) ----------
    private final UUID id;
    private final IndicatorType type;
    private final String value;

    // ---------- MÉTADONNÉES CTI (rafraîchies par les flux) ----------
    private int confidence;
    private String feedSource;
    private String externalId;
    private String description;
    private Set<String> tags;
    private Instant firstSeen;
    private Instant lastSeen;
    private Instant validUntil;

    // ---------- DÉCISION D'ANALYSTE (survit aux flux) ----------
    private boolean revoked;
    private String revocationReason;
    private Instant revokedAt;

    /**
     * Observation d'un indicateur par un flux CTI — ou saisie manuelle
     * d'un analyste, qui est le même acte du point de vue du domaine.
     * Porte l'identité ET les métadonnées ; seule la première est figée.
     */
    @Builder
    public record Observation(
            IndicatorType type,
            String value,
            int confidence,
            String feedSource,
            String externalId,
            String description,
            Set<String> tags,
            Instant observedAt,
            Instant validUntil) {
    }

    /** Point d'entrée unique : un indicateur naît de sa première observation. */
    public static Indicator declare(Observation observation) {
        if (observation == null || observation.type() == null) {
            throw new BusinessRuleViolationException(INVALID,
                    "An indicator must have a type");
        }
        Instant seenAt = observation.observedAt() == null ? Instant.now() : observation.observedAt();
        return Indicator.builder()
                .id(UUID.randomUUID())
                .type(observation.type())
                .value(observation.type().normalize(observation.value()))
                .confidence(validateConfidence(observation.confidence()))
                .feedSource(TextNormalization.lowerTrim(
                        requireFeedSource(observation.feedSource())))
                .externalId(TextNormalization.blankToNull(observation.externalId()))
                .description(observation.description())
                .tags(normalizeTags(observation.tags()))
                .firstSeen(seenAt)
                .lastSeen(seenAt)
                .validUntil(observation.validUntil())
                .revoked(false)
                .build();
    }

    /**
     * Ré-observation par un flux : l'upsert du contrat d'ingestion.
     *
     * <p>Rafraîchit les MÉTADONNÉES et rien d'autre. L'identité est
     * vérifiée avant tout : une observation qui ne porte pas exactement
     * le même couple (type, valeur normalisée) parle d'un autre
     * indicateur, et le signaler vaut mieux que d'écraser silencieusement
     * une clé de corrélation.
     *
     * <p>La dernière observation fait foi sur la fenêtre de validité :
     * c'est le flux qui sait jusqu'à quand son renseignement tient.
     */
    public void refreshFrom(Observation observation) {
        requireSameIdentity(observation);
        Instant seenAt = observation.observedAt() == null ? Instant.now() : observation.observedAt();

        this.confidence = validateConfidence(observation.confidence());
        this.feedSource = TextNormalization.lowerTrim(
                requireFeedSource(observation.feedSource()));
        if (TextNormalization.blankToNull(observation.externalId()) != null) {
            this.externalId = observation.externalId().trim();
        }
        if (TextNormalization.blankToNull(observation.description()) != null) {
            this.description = observation.description();
        }
        Set<String> merged = new LinkedHashSet<>(this.tags);
        merged.addAll(normalizeTags(observation.tags()));
        this.tags = Collections.unmodifiableSet(merged);
        if (seenAt.isBefore(this.firstSeen)) {
            this.firstSeen = seenAt;
        }
        if (seenAt.isAfter(this.lastSeen)) {
            this.lastSeen = seenAt;
        }
        this.validUntil = observation.validUntil();
        // La révocation n'est PAS levée : un flux qui continue de pousser
        // un IOC déclaré faux positif ne rouvre pas le débat tout seul.
    }

    /**
     * Décision d'analyste : cet indicateur ne doit plus enrichir aucune
     * alerte. Le motif est obligatoire — une révocation sans justification
     * est intraçable en revue, au même titre qu'une clôture de cas sans
     * conclusion.
     */
    public void revoke(String reason) {
        if (revoked) {
            throw new BusinessRuleViolationException("INDICATOR_ALREADY_REVOKED",
                    "This indicator is already revoked");
        }
        if (TextNormalization.blankToNull(reason) == null) {
            throw new BusinessRuleViolationException(INVALID,
                    "A revocation requires a reason");
        }
        this.revoked = true;
        this.revocationReason = reason.trim();
        this.revokedAt = Instant.now();
    }

    /** État déduit à l'instant demandé — voir {@link IndicatorStatus}. */
    public IndicatorStatus statusAt(Instant now) {
        if (revoked) {
            return IndicatorStatus.REVOKED;
        }
        return validUntil != null && !validUntil.isAfter(now)
                ? IndicatorStatus.EXPIRED
                : IndicatorStatus.ACTIVE;
    }

    /** Seul un indicateur ACTIVE enrichit une alerte. */
    public boolean isActionableAt(Instant now) {
        return statusAt(now) == IndicatorStatus.ACTIVE;
    }

    private void requireSameIdentity(Observation observation) {
        if (observation == null || observation.type() != this.type
                || !this.value.equals(observation.type() == null
                        ? null : observation.type().normalize(observation.value()))) {
            throw new BusinessRuleViolationException("INDICATOR_IDENTITY_MISMATCH",
                    "An observation cannot change the identity of indicator %s:%s"
                            .formatted(this.type, this.value));
        }
    }

    private static int validateConfidence(int confidence) {
        if (confidence < MIN_CONFIDENCE || confidence > MAX_CONFIDENCE) {
            throw new BusinessRuleViolationException(INVALID,
                    "Confidence must be between %d and %d".formatted(MIN_CONFIDENCE, MAX_CONFIDENCE));
        }
        return confidence;
    }

    private static String requireFeedSource(String feedSource) {
        if (TextNormalization.blankToNull(feedSource) == null) {
            throw new BusinessRuleViolationException(INVALID,
                    "An indicator must carry the source that provided it");
        }
        return feedSource;
    }

    /** Tags normalisés (minuscules, dédoublonnés) : un tag est un critère de recherche. */
    private static Set<String> normalizeTags(Set<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return Set.of();
        }
        Set<String> normalized = new LinkedHashSet<>();
        for (String tag : tags) {
            String cleaned = TextNormalization.lowerTrim(TextNormalization.blankToNull(tag));
            if (cleaned != null) {
                normalized.add(cleaned);
            }
        }
        return Collections.unmodifiableSet(normalized);
    }
}
