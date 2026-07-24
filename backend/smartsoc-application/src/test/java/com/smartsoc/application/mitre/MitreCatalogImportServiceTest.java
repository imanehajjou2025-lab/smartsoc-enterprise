package com.smartsoc.application.mitre;

import com.smartsoc.application.mitre.MitreCatalogImportService.ImportReport;
import com.smartsoc.domain.common.PageResult;
import com.smartsoc.domain.mitre.MitreCatalogRepository;
import com.smartsoc.domain.mitre.MitreTactic;
import com.smartsoc.domain.mitre.MitreTechnique;
import com.smartsoc.domain.mitre.MitreTechniqueQuery;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/**
 * Le lot d'import est TOLÉRANT : une entrée inexploitable est écartée
 * individuellement et nommée, les valides passent quand même — et un
 * ré-import met à jour au lieu de dupliquer. Testé avec un dépôt en mémoire
 * pour exercer le vrai chemin d'upsert et de rejet du domaine.
 */
class MitreCatalogImportServiceTest {

    private final InMemoryCatalog repository = new InMemoryCatalog();
    private final MitreCatalogImportService importService =
            new MitreCatalogImportService(new MitreCatalogService(repository));

    private static MitreTechnique.CatalogEntry entry(String attackId, MitreTactic... tactics) {
        return MitreTechnique.CatalogEntry.builder()
                .attackId(attackId)
                .name("Technique " + attackId)
                .tactics(Set.of(tactics))
                .build();
    }

    @Test
    void malformedEntriesAreRejectedIndividuallyWhileValidOnesAreImported() {
        ImportReport report = importService.importBatch(List.of(
                entry("T1059", MitreTactic.EXECUTION),      // ok
                entry("not-an-id", MitreTactic.EXECUTION),  // identifiant hors format
                entry("T1566"),                             // aucune tactique
                entry("T1078", MitreTactic.PERSISTENCE)));  // ok

        assertThat(report.received()).isEqualTo(4);
        assertThat(report.created()).isEqualTo(2);
        assertThat(report.updated()).isZero();
        assertThat(report.rejectedCount()).isEqualTo(2);
        assertThat(report.rejected()).extracting("index", "code").containsExactly(
                tuple(1, "INVALID_ATTACK_ID"),
                tuple(2, "INVALID_MITRE_TECHNIQUE"));
        assertThat(repository.count()).isEqualTo(2);
    }

    @Test
    void reimportUpdatesInPlaceInsteadOfDuplicating() {
        importService.importBatch(List.of(entry("T1059", MitreTactic.EXECUTION)));

        ImportReport second = importService.importBatch(List.of(
                MitreTechnique.CatalogEntry.builder()
                        .attackId("T1059").name("Renamed")
                        .tactics(Set.of(MitreTactic.EXECUTION, MitreTactic.INITIAL_ACCESS)).build()));

        assertThat(second.created()).isZero();
        assertThat(second.updated()).isEqualTo(1);
        assertThat(repository.count()).isEqualTo(1);
        assertThat(repository.findByAttackId("T1059").orElseThrow().getName()).isEqualTo("Renamed");
    }

    /** Dépôt en mémoire, indexé par identité — juste ce qu'il faut pour l'orchestration. */
    private static final class InMemoryCatalog implements MitreCatalogRepository {
        private final Map<String, MitreTechnique> byAttackId = new HashMap<>();

        @Override
        public MitreTechnique save(MitreTechnique technique) {
            byAttackId.put(technique.getAttackId(), technique);
            return technique;
        }

        @Override
        public Optional<MitreTechnique> findByAttackId(String normalizedAttackId) {
            return Optional.ofNullable(byAttackId.get(normalizedAttackId));
        }

        @Override
        public PageResult<MitreTechnique> search(MitreTechniqueQuery query) {
            List<MitreTechnique> all = List.copyOf(byAttackId.values());
            return new PageResult<>(all, all.size(), 0, Math.max(all.size(), 1));
        }

        @Override
        public long count() {
            return byAttackId.size();
        }
    }
}
