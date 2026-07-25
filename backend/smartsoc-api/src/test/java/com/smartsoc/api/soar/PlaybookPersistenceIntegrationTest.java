package com.smartsoc.api.soar;

import com.smartsoc.TestcontainersConfiguration;
import com.smartsoc.domain.soar.Playbook;
import com.smartsoc.domain.soar.PlaybookQuery;
import com.smartsoc.domain.soar.PlaybookRepository;
import com.smartsoc.domain.soar.PlaybookStepTemplate;
import com.smartsoc.domain.common.PageQuery;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Persistance du playbook sur PostgreSQL réel : l'aller-retour JSONB d'un
 * record simple ({@code PlaybookStepTemplate}) via Jackson natif, sans
 * codec dédié (contraste avec l'arbre scellé de Hunting).
 */
@SpringBootTest(properties = "smartsoc.security.bootstrap-admin.password=IntegrationTest123!")
@Import(TestcontainersConfiguration.class)
class PlaybookPersistenceIntegrationTest {

    @Autowired
    private PlaybookRepository repository;

    @Test
    void savesAndReloadsStepsWithOrderTitleAndDescription() {
        Playbook playbook = Playbook.declare(Playbook.DeclareCommand.builder()
                .name("Confinement ransomware")
                .description("Contenir puis analyser")
                .steps(List.of(
                        new PlaybookStepTemplate(0, "Isoler l'hôte", "Débrancher le réseau"),
                        new PlaybookStepTemplate(0, "Notifier l'équipe", null)))
                .build());

        Playbook saved = repository.save(playbook);
        Playbook reloaded = repository.findById(saved.getId()).orElseThrow();

        assertThat(reloaded.getSteps()).hasSize(2);
        assertThat(reloaded.getSteps().get(0)).isEqualTo(
                new PlaybookStepTemplate(0, "Isoler l'hôte", "Débrancher le réseau"));
        assertThat(reloaded.getSteps().get(1).title()).isEqualTo("Notifier l'équipe");
        assertThat(reloaded.getSteps().get(1).description()).isNull();
        assertThat(reloaded.getVersion()).isEqualTo(1);
        assertThat(reloaded.isArchived()).isFalse();
    }

    @Test
    void archivedPlaybooksAreExcludedBySearchByDefault() {
        Playbook playbook = Playbook.declare(Playbook.DeclareCommand.builder()
                .name("Archive test " + java.util.UUID.randomUUID())
                .steps(List.of(new PlaybookStepTemplate(0, "Étape", null)))
                .build());
        playbook.archive();
        Playbook saved = repository.save(playbook);

        var byDefault = repository.search(new PlaybookQuery(saved.getName(), false, PageQuery.of(0, 10)));
        var withArchived = repository.search(new PlaybookQuery(saved.getName(), true, PageQuery.of(0, 10)));

        assertThat(byDefault.items()).isEmpty();
        assertThat(withArchived.items()).extracting(Playbook::getId).contains(saved.getId());
    }
}
