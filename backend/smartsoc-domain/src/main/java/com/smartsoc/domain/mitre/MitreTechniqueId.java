package com.smartsoc.domain.mitre;

import com.smartsoc.domain.common.BusinessRuleViolationException;

import java.util.Locale;
import java.util.Optional;

/**
 * Normalisation et validation d'un identifiant de technique ATT&CK —
 * la CLÉ DE CORRÉLATION du module, exactement le rôle que joue le couple
 * (type, valeur) pour un IOC ou le hostname pour un actif.
 *
 * <p>Les identifiants arrivent de deux sources externes qu'on ne maîtrise
 * pas : le bundle ATT&CK importé et les chaînes brutes déjà portées par
 * {@code Alert.mitreTechniques}. Les deux côtés de la corrélation DOIVENT
 * être normalisés de la même façon, sinon un « t1059 » d'alerte ne
 * rejoint jamais le « T1059 » du catalogue — l'échec silencieux habituel.
 *
 * <p>Format ATT&CK : {@code T} + 4 chiffres pour une technique
 * ({@code T1059}), suffixé de {@code .} + 3 chiffres pour une
 * sous-technique ({@code T1059.001}). Longueur donc rigoureusement bornée
 * à 9 caractères. La validation se fait caractère par caractère, en temps
 * linéaire garanti : l'entrée venant d'un tiers, aucune expression
 * régulière à rétro-suivi (java:S8786).
 */
public final class MitreTechniqueId {

    private static final String INVALID = "INVALID_ATTACK_ID";
    private static final int TECHNIQUE_DIGITS = 4;
    private static final int SUBTECHNIQUE_DIGITS = 3;
    /** {@code T} + 4 chiffres (technique de base). */
    private static final int TECHNIQUE_LENGTH = 1 + TECHNIQUE_DIGITS;
    /** {@code T} + 4 chiffres + {@code .} + 3 chiffres (sous-technique). */
    private static final int MAX_LENGTH = TECHNIQUE_LENGTH + 1 + SUBTECHNIQUE_DIGITS;

    private MitreTechniqueId() {
    }

    /**
     * Forme canonique de l'identifiant (majuscules, sans espaces de bord),
     * ou rejet de ce qui n'est pas un identifiant ATT&CK. C'est cette
     * forme, et elle seule, qui est stockée et comparée.
     */
    public static String normalize(String rawId) {
        if (rawId == null || rawId.isBlank()) {
            throw new BusinessRuleViolationException(INVALID,
                    "A technique identifier is required");
        }
        String candidate = rawId.trim().toUpperCase(Locale.ROOT);
        if (!isWellFormed(candidate)) {
            throw new BusinessRuleViolationException(INVALID,
                    "'%s' is not a MITRE ATT&CK technique identifier (expected T#### or T####.###)"
                            .formatted(rawId.trim()));
        }
        return candidate;
    }

    /**
     * Vrai si la chaîne est un identifiant ATT&CK bien formé. Bornée en
     * longueur d'abord, puis lue caractère par caractère — linéaire par
     * construction, sans expression régulière.
     */
    private static boolean isWellFormed(String candidate) {
        int length = candidate.length();
        if (length > MAX_LENGTH || candidate.charAt(0) != 'T') {
            return false;
        }
        // Les 4 chiffres de la technique.
        for (int i = 1; i <= TECHNIQUE_DIGITS; i++) {
            if (i >= length || !isDigit(candidate.charAt(i))) {
                return false;
            }
        }
        // Technique seule : « T#### » et rien d'autre.
        if (length == TECHNIQUE_LENGTH) {
            return true;
        }
        // Sinon, une sous-technique : « . » puis exactement 3 chiffres.
        if (length != MAX_LENGTH || candidate.charAt(TECHNIQUE_LENGTH) != '.') {
            return false;
        }
        for (int i = TECHNIQUE_LENGTH + 1; i < length; i++) {
            if (!isDigit(candidate.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    private static boolean isDigit(char c) {
        return c >= '0' && c <= '9';
    }

    /** Vrai si l'identifiant NORMALISÉ désigne une sous-technique ({@code T####.###}). */
    public static boolean isSubTechnique(String normalizedId) {
        return normalizedId != null && normalizedId.length() == MAX_LENGTH;
    }

    /**
     * Identifiant de la technique parente d'une sous-technique NORMALISÉE
     * ({@code T1059.001} → {@code T1059}), ou {@link Optional#empty()} si
     * l'identifiant est déjà une technique de base.
     */
    public static Optional<String> parentId(String normalizedId) {
        if (!isSubTechnique(normalizedId)) {
            return Optional.empty();
        }
        return Optional.of(normalizedId.substring(0, TECHNIQUE_LENGTH));
    }
}
