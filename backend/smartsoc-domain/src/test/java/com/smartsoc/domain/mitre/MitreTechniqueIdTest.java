package com.smartsoc.domain.mitre;

import com.smartsoc.domain.common.BusinessRuleViolationException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * La clé de corrélation du module. Chaque cas ci-dessous est un
 * rapprochement qui échouerait SILENCIEUSEMENT si la normalisation
 * divergeait entre le catalogue importé et les techniques brutes des
 * alertes ({@code Alert.mitreTechniques}).
 */
class MitreTechniqueIdTest {

    @Test
    void normalizesToUpperCaseTrimmedForm() {
        assertThat(MitreTechniqueId.normalize("  t1059 ")).isEqualTo("T1059");
        assertThat(MitreTechniqueId.normalize("t1059.001")).isEqualTo("T1059.001");
        assertThat(MitreTechniqueId.normalize("T1059")).isEqualTo("T1059");
    }

    @Test
    void rejectsAnythingThatIsNotAnAttackId() {
        assertThatThrownBy(() -> MitreTechniqueId.normalize(null))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasFieldOrPropertyWithValue("code", "INVALID_ATTACK_ID");
        assertThatThrownBy(() -> MitreTechniqueId.normalize("   "))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasFieldOrPropertyWithValue("code", "INVALID_ATTACK_ID");

        // Trop court, trop long, sans « T », un ID de tactique, un mauvais
        // nombre de chiffres de sous-technique, un suffixe non numérique.
        for (String malformed : new String[] {
                "1059", "T105", "T10599", "TA0001",
                "T1059.01", "T1059.0011", "T1059.", "T1059.abc", "technique"}) {
            assertThatThrownBy(() -> MitreTechniqueId.normalize(malformed))
                    .as("must reject '%s'", malformed)
                    .isInstanceOf(BusinessRuleViolationException.class)
                    .hasFieldOrPropertyWithValue("code", "INVALID_ATTACK_ID");
        }
    }

    @Test
    void distinguishesSubTechniquesAndResolvesParent() {
        assertThat(MitreTechniqueId.isSubTechnique("T1059")).isFalse();
        assertThat(MitreTechniqueId.isSubTechnique("T1059.001")).isTrue();
        assertThat(MitreTechniqueId.parentId("T1059.001")).contains("T1059");
        assertThat(MitreTechniqueId.parentId("T1059")).isEmpty();
    }
}
