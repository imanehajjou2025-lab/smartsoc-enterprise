package com.smartsoc.domain.hunting;

import com.smartsoc.domain.alerts.AlertStatus;
import com.smartsoc.domain.alerts.Severity;
import com.smartsoc.domain.common.BusinessRuleViolationException;
import com.smartsoc.domain.mitre.MitreTechniqueId;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

/**
 * Dimension chassable d'une alerte — et, indissociablement, les opérateurs
 * qu'elle accepte et la façon de VALIDER/NORMALISER sa valeur. Même
 * doctrine que {@code IndicatorType} pour les IOC : la nature d'un champ
 * détermine sa propre règle, un seul endroit à faire évoluer.
 *
 * <p>{@code MITRE_TECHNIQUE} réutilise directement
 * {@link MitreTechniqueId#normalize(String)} : la corrélation ATT&CK d'une
 * chasse doit comparer exactement la même clé que le catalogue MITRE,
 * jamais une forme dérivée qui divergerait silencieusement.
 */
public enum HuntField {

    SEVERITY(EnumSet.of(HuntOperator.EQUALS)),
    STATUS(EnumSet.of(HuntOperator.EQUALS)),
    SOURCE(EnumSet.of(HuntOperator.EQUALS, HuntOperator.CONTAINS)),
    HOSTNAME(EnumSet.of(HuntOperator.EQUALS, HuntOperator.CONTAINS)),
    RULE_ID(EnumSet.of(HuntOperator.EQUALS, HuntOperator.CONTAINS)),
    DETECTED_AT(EnumSet.of(HuntOperator.GREATER_THAN, HuntOperator.LESS_THAN)),
    MITRE_TECHNIQUE(EnumSet.of(HuntOperator.CONTAINS)),
    /**
     * Recherche texte dans l'événement brut intégral (JSONB
     * {@code alerts.raw_payload}) — le champ prévu dès le jalon Alertes A1
     * précisément pour la chasse. Un {@code ILIKE} non indexé en V1 (limite
     * assumée et documentée, comme la limitation FQDN des actifs) : un
     * index trigram GIN reste une évolution si le volume l'exige.
     */
    RAW_PAYLOAD_TEXT(EnumSet.of(HuntOperator.CONTAINS));

    private static final String INVALID_VALUE = "HUNT_INVALID_VALUE";
    private static final int MAX_TEXT_VALUE_LENGTH = 2048;

    private final Set<HuntOperator> allowedOperators;

    HuntField(Set<HuntOperator> allowedOperators) {
        this.allowedOperators = allowedOperators;
    }

    public Set<HuntOperator> allowedOperators() {
        return allowedOperators;
    }

    public boolean supports(HuntOperator operator) {
        return allowedOperators.contains(operator);
    }

    /**
     * Valide et normalise une valeur brute selon ce champ. C'est cette
     * forme, et elle seule, qui est stockée et comparée par l'adaptateur
     * d'exécution — celui-ci n'a plus à revalider.
     */
    public String normalize(String rawValue) {
        String value = require(rawValue);
        return switch (this) {
            case SEVERITY -> validEnum(value, Severity.class);
            case STATUS -> validEnum(value, AlertStatus.class);
            case DETECTED_AT -> validInstant(value);
            case MITRE_TECHNIQUE -> MitreTechniqueId.normalize(value);
            case SOURCE, HOSTNAME, RULE_ID, RAW_PAYLOAD_TEXT -> value;
        };
    }

    private static String require(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            throw new BusinessRuleViolationException(INVALID_VALUE, "A hunt condition value is required");
        }
        String trimmed = rawValue.trim();
        if (trimmed.length() > MAX_TEXT_VALUE_LENGTH) {
            throw new BusinessRuleViolationException(INVALID_VALUE,
                    "A hunt condition value must not exceed %d characters".formatted(MAX_TEXT_VALUE_LENGTH));
        }
        return trimmed;
    }

    private static <E extends Enum<E>> String validEnum(String value, Class<E> type) {
        try {
            return Enum.valueOf(type, value.toUpperCase(Locale.ROOT)).name();
        } catch (IllegalArgumentException e) {
            throw new BusinessRuleViolationException(INVALID_VALUE,
                    "'%s' is not a valid %s".formatted(value, type.getSimpleName()));
        }
    }

    private static String validInstant(String value) {
        try {
            return Instant.parse(value).toString();
        } catch (DateTimeParseException e) {
            throw new BusinessRuleViolationException(INVALID_VALUE,
                    "'%s' is not a valid ISO-8601 instant (e.g. 2026-07-25T10:00:00Z)".formatted(value));
        }
    }
}
