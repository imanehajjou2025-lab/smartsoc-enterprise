package com.smartsoc.domain.mitre;

import java.util.Locale;
import java.util.Optional;

/**
 * Les tactiques de la matrice MITRE ATT&CK Enterprise — le « pourquoi »
 * d'une technique, les colonnes de la matrice.
 *
 * <p>Vocabulaire FINI et STABLE (14 tactiques depuis des années), donc un
 * enum comme {@link com.smartsoc.domain.alerts.Severity} ou
 * {@code IndicatorType} : pas une table à maintenir. L'ordre de
 * déclaration est l'ordre des colonnes de la matrice — de la
 * reconnaissance à l'impact — et sert tel quel à la heatmap de couverture.
 *
 * <p>Chaque tactique porte son identifiant ATT&CK ({@code TAxxxx}) et son
 * {@code shortName} STIX ({@code x_mitre_shortname}) : c'est ce dernier qui
 * relie une technique à ses tactiques dans un bundle ATT&CK
 * ({@code kill_chain_phases}), jamais le libellé humain.
 */
public enum MitreTactic {

    RECONNAISSANCE("TA0043", "reconnaissance", "Reconnaissance"),
    RESOURCE_DEVELOPMENT("TA0042", "resource-development", "Resource Development"),
    INITIAL_ACCESS("TA0001", "initial-access", "Initial Access"),
    EXECUTION("TA0002", "execution", "Execution"),
    PERSISTENCE("TA0003", "persistence", "Persistence"),
    PRIVILEGE_ESCALATION("TA0004", "privilege-escalation", "Privilege Escalation"),
    DEFENSE_EVASION("TA0005", "defense-evasion", "Defense Evasion"),
    CREDENTIAL_ACCESS("TA0006", "credential-access", "Credential Access"),
    DISCOVERY("TA0007", "discovery", "Discovery"),
    LATERAL_MOVEMENT("TA0008", "lateral-movement", "Lateral Movement"),
    COLLECTION("TA0009", "collection", "Collection"),
    COMMAND_AND_CONTROL("TA0011", "command-and-control", "Command and Control"),
    EXFILTRATION("TA0010", "exfiltration", "Exfiltration"),
    IMPACT("TA0040", "impact", "Impact");

    private final String attackId;
    private final String shortName;
    private final String displayName;

    MitreTactic(String attackId, String shortName, String displayName) {
        this.attackId = attackId;
        this.shortName = shortName;
        this.displayName = displayName;
    }

    public String attackId() {
        return attackId;
    }

    public String shortName() {
        return shortName;
    }

    public String displayName() {
        return displayName;
    }

    /**
     * Tactique portant ce {@code shortName} STIX — le lien technique →
     * tactique d'un bundle ATT&CK. Tolérant : un shortName inconnu (autre
     * domaine, tactique retirée) renvoie {@link Optional#empty()} plutôt
     * que d'échouer, pour que l'import écarte l'entrée sans tomber.
     */
    public static Optional<MitreTactic> fromShortName(String shortName) {
        if (shortName == null) {
            return Optional.empty();
        }
        String normalized = shortName.trim().toLowerCase(Locale.ROOT);
        for (MitreTactic tactic : values()) {
            if (tactic.shortName.equals(normalized)) {
                return Optional.of(tactic);
            }
        }
        return Optional.empty();
    }

    /** Tactique portant cet identifiant ATT&CK ({@code TAxxxx}), insensible à la casse et aux espaces. */
    public static Optional<MitreTactic> fromAttackId(String attackId) {
        if (attackId == null) {
            return Optional.empty();
        }
        String normalized = attackId.trim().toUpperCase(Locale.ROOT);
        for (MitreTactic tactic : values()) {
            if (tactic.attackId.equals(normalized)) {
                return Optional.of(tactic);
            }
        }
        return Optional.empty();
    }
}
