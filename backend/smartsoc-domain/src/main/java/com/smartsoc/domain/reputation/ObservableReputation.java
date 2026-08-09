package com.smartsoc.domain.reputation;

import com.smartsoc.domain.common.BusinessRuleViolationException;
import com.smartsoc.domain.common.TextNormalization;
import com.smartsoc.domain.intelligence.IndicatorType;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Réputation d'un observable, mise en cache (ADR-014 phase 3) —
 * consultée à la demande d'un analyste (jamais sur le flux, R4), jamais
 * automatique. Le cache est **obligatoire**, pas une optimisation : il
 * protège le quota strict de VirusTotal (4 requêtes/min, 500/jour en
 * offre gratuite) — voir {@code ObservableReputationService} pour la
 * décision fraîcheur-du-cache-vs-nouvel-appel.
 *
 * <p><b>Identité (immuable) :</b> {@code (source, type, valeur normalisée)}
 * — même vocabulaire de type que {@link IndicatorType}, pour rester le
 * même observable qu'un indicateur ou qu'un IOC d'alerte (voir
 * {@code Observable}, contexte intelligence).
 */
@Getter
@Builder(toBuilder = true)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ObservableReputation {

    private static final String INVALID = "INVALID_OBSERVABLE_REPUTATION";

    private final UUID id;
    private final String source;
    private final IndicatorType type;
    private final String value;

    private ReputationVerdict verdict;
    private int maliciousCount;
    private int suspiciousCount;
    private int harmlessCount;
    private int undetectedCount;
    private final Instant firstCheckedAt;
    private Instant checkedAt;

    /** Un relevé du scanner — les compteurs bruts, jamais un verdict déjà tranché par la source. */
    @Builder
    public record Lookup(
            String source,
            IndicatorType type,
            String value,
            int maliciousCount,
            int suspiciousCount,
            int harmlessCount,
            int undetectedCount,
            Instant checkedAt) {
    }

    /** Point d'entrée unique : une réputation naît de son premier relevé. */
    public static ObservableReputation record(Lookup lookup) {
        requireLookup(lookup);
        Instant checkedAt = lookup.checkedAt() == null ? Instant.now() : lookup.checkedAt();
        return ObservableReputation.builder()
                .id(UUID.randomUUID())
                .source(TextNormalization.lowerTrimRequired(requireNonBlank(lookup.source(), "source")))
                .type(Objects.requireNonNull(lookup.type(), "type"))
                .value(requireNonBlank(lookup.value(), "value").trim())
                .verdict(deriveVerdict(lookup))
                .maliciousCount(lookup.maliciousCount())
                .suspiciousCount(lookup.suspiciousCount())
                .harmlessCount(lookup.harmlessCount())
                .undetectedCount(lookup.undetectedCount())
                .firstCheckedAt(checkedAt)
                .checkedAt(checkedAt)
                .build();
    }

    /** Re-relevé (cache expiré) : rafraîchit les compteurs et le verdict qui en découle. */
    public void refreshFrom(Lookup lookup) {
        requireSameIdentity(lookup);
        this.verdict = deriveVerdict(lookup);
        this.maliciousCount = lookup.maliciousCount();
        this.suspiciousCount = lookup.suspiciousCount();
        this.harmlessCount = lookup.harmlessCount();
        this.undetectedCount = lookup.undetectedCount();
        Instant checkedAt = lookup.checkedAt() == null ? Instant.now() : lookup.checkedAt();
        if (checkedAt.isAfter(this.checkedAt)) {
            this.checkedAt = checkedAt;
        }
    }

    /** Vrai si ce relevé a été fait avant l'instant limite — sert à décider s'il faut re-interroger la source. */
    public boolean isStaleAt(Instant staleBefore) {
        return checkedAt.isBefore(staleBefore);
    }

    /**
     * Pire cas l'emporte : un seul moteur qui signale malveillant suffit.
     * Voir {@link ReputationVerdict} pour la justification de cette
     * traduction assumée.
     */
    private static ReputationVerdict deriveVerdict(Lookup lookup) {
        if (lookup.maliciousCount() > 0) {
            return ReputationVerdict.MALICIOUS;
        }
        if (lookup.suspiciousCount() > 0) {
            return ReputationVerdict.SUSPICIOUS;
        }
        if (lookup.harmlessCount() > 0) {
            return ReputationVerdict.HARMLESS;
        }
        return ReputationVerdict.UNDETECTED;
    }

    private void requireSameIdentity(Lookup lookup) {
        if (lookup == null
                || !this.source.equals(TextNormalization.lowerTrim(lookup.source()))
                || this.type != lookup.type()
                || !this.value.equals(lookup.value())) {
            throw new BusinessRuleViolationException("OBSERVABLE_REPUTATION_IDENTITY_MISMATCH",
                    "A lookup cannot change the identity of reputation %s:%s:%s"
                            .formatted(this.source, this.type, this.value));
        }
    }

    private static void requireLookup(Lookup lookup) {
        if (lookup == null) {
            throw new BusinessRuleViolationException(INVALID, "A lookup is required");
        }
    }

    private static String requireNonBlank(String value, String field) {
        String cleaned = TextNormalization.blankToNull(value);
        if (cleaned == null) {
            throw new BusinessRuleViolationException(INVALID,
                    "Field '%s' must not be blank".formatted(field));
        }
        return cleaned;
    }
}
