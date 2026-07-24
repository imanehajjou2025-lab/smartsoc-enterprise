package com.smartsoc.domain.mitre;

import com.smartsoc.domain.common.BusinessRuleViolationException;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MitreTechniqueTest {

    private static MitreTechnique.CatalogEntry.CatalogEntryBuilder entry() {
        return MitreTechnique.CatalogEntry.builder()
                .attackId("T1059")
                .name("Command and Scripting Interpreter")
                .description("Adversaries may abuse command and script interpreters.")
                .url("https://attack.mitre.org/techniques/T1059/")
                .tactics(Set.of(MitreTactic.EXECUTION))
                .attackVersion("16.1");
    }

    @Test
    void fromCatalogNormalizesIdentityAndKeepsMetadata() {
        MitreTechnique technique = MitreTechnique.fromCatalog(
                entry().attackId("  t1059 ").build());

        assertThat(technique.getId()).isNotNull();
        assertThat(technique.getAttackId()).isEqualTo("T1059");
        assertThat(technique.isSubTechnique()).isFalse();
        assertThat(technique.getParentId()).isNull();
        assertThat(technique.getName()).isEqualTo("Command and Scripting Interpreter");
        assertThat(technique.getTactics()).containsExactly(MitreTactic.EXECUTION);
        assertThat(technique.isDeprecated()).isFalse();
    }

    @Test
    void subTechniqueDerivesParentFromItsIdentity() {
        MitreTechnique sub = MitreTechnique.fromCatalog(
                entry().attackId("T1059.001").name("PowerShell").build());

        assertThat(sub.isSubTechnique()).isTrue();
        assertThat(sub.getParentId()).isEqualTo("T1059");
    }

    @Test
    void refreshFromUpdatesMetadataButNeverIdentity() {
        MitreTechnique technique = MitreTechnique.fromCatalog(entry().build());

        technique.refreshFrom(entry()
                .name("Command and Scripting Interpreter (renamed)")
                .tactics(Set.of(MitreTactic.EXECUTION, MitreTactic.INITIAL_ACCESS))
                .attackVersion("17.0")
                .build());

        assertThat(technique.getName()).endsWith("(renamed)");
        assertThat(technique.getTactics())
                .containsExactlyInAnyOrder(MitreTactic.EXECUTION, MitreTactic.INITIAL_ACCESS);
        assertThat(technique.getAttackVersion()).isEqualTo("17.0");
    }

    @Test
    void refreshFromRejectsAForeignIdentity() {
        MitreTechnique technique = MitreTechnique.fromCatalog(entry().build());
        MitreTechnique.CatalogEntry foreign = entry().attackId("T1566").build();

        assertThatThrownBy(() -> technique.refreshFrom(foreign))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasFieldOrPropertyWithValue("code", "MITRE_TECHNIQUE_IDENTITY_MISMATCH");
    }

    @Test
    void deprecationFollowsTheImportBecauseItIsAnAttackFact() {
        // Contraste avec la révocation d'un IOC (décision d'analyste qui
        // survit au flux) : la dépréciation est un fait DU référentiel, donc
        // l'import fait foi dans les deux sens.
        MitreTechnique technique = MitreTechnique.fromCatalog(entry().build());

        technique.refreshFrom(entry().deprecated(true).build());
        assertThat(technique.isDeprecated()).isTrue();

        technique.refreshFrom(entry().deprecated(false).build());
        assertThat(technique.isDeprecated()).isFalse();
    }

    @Test
    void requiresANameAndAtLeastOneTactic() {
        MitreTechnique.CatalogEntry noName = entry().name("  ").build();
        MitreTechnique.CatalogEntry noTactic = entry().tactics(Set.of()).build();

        assertThatThrownBy(() -> MitreTechnique.fromCatalog(noName))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasFieldOrPropertyWithValue("code", "INVALID_MITRE_TECHNIQUE");
        assertThatThrownBy(() -> MitreTechnique.fromCatalog(noTactic))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasFieldOrPropertyWithValue("code", "INVALID_MITRE_TECHNIQUE");
    }

    @Test
    void exposedTacticsAreUnmodifiable() {
        MitreTechnique technique = MitreTechnique.fromCatalog(entry().build());

        assertThatThrownBy(() -> technique.getTactics().add(MitreTactic.IMPACT))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
