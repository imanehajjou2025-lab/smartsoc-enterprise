package com.smartsoc.domain.mitre;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verrou du vocabulaire de la matrice : les 14 tactiques Enterprise, dans
 * l'ordre des colonnes. Tout ajout, retrait ou réordonnancement devient un
 * choix EXPLICITE — la heatmap de couverture et le mapping des imports en
 * dépendent.
 */
class MitreTacticTest {

    @Test
    void enterpriseMatrixIsLockedToFourteenTacticsInColumnOrder() {
        MitreTactic[] tactics = MitreTactic.values();

        assertThat(tactics).hasSize(14);
        assertThat(tactics).extracting(MitreTactic::attackId).containsExactly(
                "TA0043", "TA0042", "TA0001", "TA0002", "TA0003", "TA0004", "TA0005",
                "TA0006", "TA0007", "TA0008", "TA0009", "TA0011", "TA0010", "TA0040");
        assertThat(tactics).extracting(MitreTactic::shortName).containsExactly(
                "reconnaissance", "resource-development", "initial-access", "execution",
                "persistence", "privilege-escalation", "defense-evasion", "credential-access",
                "discovery", "lateral-movement", "collection", "command-and-control",
                "exfiltration", "impact");
    }

    @Test
    void resolvesTacticFromStixShortNameCaseAndSpaceInsensitively() {
        assertThat(MitreTactic.fromShortName("  Command-And-Control "))
                .contains(MitreTactic.COMMAND_AND_CONTROL);
        assertThat(MitreTactic.fromShortName("initial-access"))
                .contains(MitreTactic.INITIAL_ACCESS);
        assertThat(MitreTactic.fromShortName("unknown-tactic")).isEmpty();
        assertThat(MitreTactic.fromShortName(null)).isEmpty();
    }

    @Test
    void resolvesTacticFromAttackId() {
        assertThat(MitreTactic.fromAttackId(" ta0002 ")).contains(MitreTactic.EXECUTION);
        assertThat(MitreTactic.fromAttackId("TA9999")).isEmpty();
        assertThat(MitreTactic.fromAttackId(null)).isEmpty();
    }
}
