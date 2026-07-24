package com.smartsoc.api.mitre;

import com.smartsoc.TestcontainersConfiguration;
import com.smartsoc.application.mitre.MitreCatalogService;
import com.smartsoc.domain.mitre.MitreTactic;
import com.smartsoc.domain.mitre.MitreTechnique;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Le semis embarqué peuple la matrice au démarrage (mode « démoable seule »).
 * Vérifie toute la chaîne sur un vrai PostgreSQL : lecture de la ressource →
 * résolution des shortnames STIX → import de lot tolérant → persistance.
 */
@SpringBootTest(properties = "smartsoc.security.bootstrap-admin.password=IntegrationTest123!")
@Import(TestcontainersConfiguration.class)
class MitreCatalogSeedRunnerIntegrationTest {

    @Autowired
    private MitreCatalogService catalogService;

    @Test
    void bundledSeedPopulatesTheCatalogAcrossTactics() {
        // Le runner a tourné au démarrage (seed-on-startup actif par défaut).
        assertThat(catalogService.count()).isGreaterThanOrEqualTo(20);

        MitreTechnique powershell = catalogService.getTechnique("T1059.001");
        assertThat(powershell.getName()).isEqualTo("PowerShell");
        assertThat(powershell.isSubTechnique()).isTrue();
        assertThat(powershell.getParentId()).isEqualTo("T1059");
        assertThat(powershell.getTactics()).contains(MitreTactic.EXECUTION);

        // Une technique multi-tactiques garde toutes ses tactiques.
        assertThat(catalogService.getTechnique("T1078").getTactics())
                .contains(MitreTactic.PERSISTENCE, MitreTactic.PRIVILEGE_ESCALATION);

        // Une technique dépréciée est présente mais marquée (doctrine no-delete).
        assertThat(catalogService.getTechnique("T1064").isDeprecated()).isTrue();
    }
}
