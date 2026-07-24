package com.smartsoc.api.mitre;

import com.smartsoc.TestcontainersConfiguration;
import com.smartsoc.domain.common.PageQuery;
import com.smartsoc.domain.mitre.MitreCatalogRepository;
import com.smartsoc.domain.mitre.MitreTactic;
import com.smartsoc.domain.mitre.MitreTechnique;
import com.smartsoc.domain.mitre.MitreTechniqueQuery;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Persistance du catalogue ATT&CK sur un vrai PostgreSQL : aller-retour
 * JSONB des tactiques, unicité de l'identité (upsert par {@code attackId}),
 * filtre par tactique via le containment GIN, et exclusion des techniques
 * dépréciées par défaut.
 *
 * <p>Identifiants factices {@code T90xx} : format valide, mais inexistants
 * dans la matrice réelle — aucune collision possible avec un futur semis du
 * catalogue.
 */
@SpringBootTest(properties = {
        "smartsoc.security.bootstrap-admin.password=IntegrationTest123!",
        // Catalogue vide et déterministe : ce test vérifie l'adaptateur, pas
        // le semis (couvert par MitreCatalogSeedRunnerIntegrationTest).
        "smartsoc.mitre.seed-on-startup=false"})
@Import(TestcontainersConfiguration.class)
class MitreCatalogPersistenceIntegrationTest {

    @Autowired
    private MitreCatalogRepository catalog;

    private static MitreTechnique technique(String attackId, String name,
                                            boolean deprecated, MitreTactic... tactics) {
        return MitreTechnique.fromCatalog(MitreTechnique.CatalogEntry.builder()
                .attackId(attackId)
                .name(name)
                .description(name + " — adversary behaviour")
                .url("https://attack.mitre.org/techniques/" + attackId.replace('.', '/') + "/")
                .tactics(Set.of(tactics))
                .deprecated(deprecated)
                .attackVersion("16.1")
                .build());
    }

    @Test
    void savesAndReloadsATechniqueWithItsTacticsAndSubTechniqueIdentity() {
        catalog.save(technique("T9001", "Parent technique", false, MitreTactic.EXECUTION));
        catalog.save(technique("T9001.011", "Sub technique", false,
                MitreTactic.EXECUTION, MitreTactic.DEFENSE_EVASION));

        MitreTechnique parent = catalog.findByAttackId("T9001").orElseThrow();
        assertThat(parent.isSubTechnique()).isFalse();
        assertThat(parent.getParentId()).isNull();
        assertThat(parent.getTactics()).containsExactly(MitreTactic.EXECUTION);

        MitreTechnique child = catalog.findByAttackId("T9001.011").orElseThrow();
        assertThat(child.isSubTechnique()).isTrue();
        assertThat(child.getParentId()).isEqualTo("T9001");
        assertThat(child.getName()).isEqualTo("Sub technique");
        assertThat(child.getUrl()).contains("T9001/011");
        // Aller-retour JSONB : le Set traverse en List et revient intact.
        assertThat(child.getTactics())
                .containsExactlyInAnyOrder(MitreTactic.EXECUTION, MitreTactic.DEFENSE_EVASION);
    }

    @Test
    void identityIsUniqueAndReImportRefreshesInPlace() {
        catalog.save(technique("T9002", "Original name", false, MitreTactic.PERSISTENCE));

        // Ré-import : on retrouve l'enregistrement par identité et on
        // rafraîchit ses métadonnées — même id, pas de doublon.
        MitreTechnique reloaded = catalog.findByAttackId("T9002").orElseThrow();
        reloaded.refreshFrom(MitreTechnique.CatalogEntry.builder()
                .attackId("T9002")
                .name("Refreshed name")
                .tactics(Set.of(MitreTactic.PERSISTENCE, MitreTactic.PRIVILEGE_ESCALATION))
                .attackVersion("17.0")
                .build());
        catalog.save(reloaded);

        MitreTechnique afterRefresh = catalog.findByAttackId("T9002").orElseThrow();
        assertThat(afterRefresh.getName()).isEqualTo("Refreshed name");
        assertThat(afterRefresh.getId()).isEqualTo(reloaded.getId());
        assertThat(afterRefresh.getTactics())
                .containsExactlyInAnyOrder(MitreTactic.PERSISTENCE, MitreTactic.PRIVILEGE_ESCALATION);

        // Une SECONDE technique de même identifiant (nouvel id) est refusée
        // par ux_mitre_attack_id : l'identité ne se duplique jamais.
        MitreTechnique impostor = technique("T9002", "Impostor", false, MitreTactic.PERSISTENCE);
        assertThatThrownBy(() -> catalog.save(impostor))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void searchFiltersByTacticThroughTheJsonbContainmentIndex() {
        catalog.save(technique("T9003", "Application layer protocol", false,
                MitreTactic.COMMAND_AND_CONTROL));
        catalog.save(technique("T9004", "Data destruction", false, MitreTactic.IMPACT));

        var c2 = catalog.search(new MitreTechniqueQuery(
                MitreTactic.COMMAND_AND_CONTROL, null, true, PageQuery.of(0, 200)));

        assertThat(c2.items()).extracting(MitreTechnique::getAttackId).contains("T9003");
        assertThat(c2.items()).extracting(MitreTechnique::getAttackId).doesNotContain("T9004");
        // Le filtre est correct quel que soit ce que d'autres tests ajoutent :
        // TOUT résultat porte la tactique demandée.
        assertThat(c2.items())
                .allSatisfy(t -> assertThat(t.getTactics()).contains(MitreTactic.COMMAND_AND_CONTROL));
    }

    @Test
    void deprecatedTechniquesAreHiddenByDefaultButRemainQueryable() {
        catalog.save(technique("T9005", "Current technique", false, MitreTactic.LATERAL_MOVEMENT));
        catalog.save(technique("T9006", "Deprecated technique", true, MitreTactic.LATERAL_MOVEMENT));

        var byDefault = catalog.search(new MitreTechniqueQuery(
                MitreTactic.LATERAL_MOVEMENT, null, false, PageQuery.of(0, 200)));
        assertThat(byDefault.items()).extracting(MitreTechnique::getAttackId)
                .contains("T9005").doesNotContain("T9006");

        var withDeprecated = catalog.search(new MitreTechniqueQuery(
                MitreTactic.LATERAL_MOVEMENT, null, true, PageQuery.of(0, 200)));
        assertThat(withDeprecated.items()).extracting(MitreTechnique::getAttackId)
                .contains("T9005", "T9006");
    }
}
