package com.smartsoc.application.mitre;

import com.smartsoc.application.mitre.MitreCatalogService.ImportResult;
import com.smartsoc.domain.common.DomainException;
import com.smartsoc.domain.mitre.MitreTechnique;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Import d'un LOT du catalogue ATT&CK — un bundle poussé par un import
 * manuel, ou le semis embarqué au démarrage.
 *
 * <p><b>Tolérance par élément, volontairement</b> — mêmes raisons qu'en CTI
 * ({@code IndicatorFeedIngestionService}) : un bundle réel peut contenir des
 * entrées inexploitables (identifiant hors format, aucune tactique
 * reconnue), et refuser le lot entier priverait la matrice de toutes les
 * techniques valides. Chaque entrée est importée dans SA transaction, chaque
 * rejet est nommé pour que la source puisse être corrigée.
 *
 * <p>Cette classe est SÉPARÉE de {@link MitreCatalogService} et NON
 * transactionnelle : en PostgreSQL une violation de contrainte rend la
 * transaction courante irrécupérable (il faut une transaction par élément),
 * et un appel interne à une méthode {@code @Transactional} du même bean
 * court-circuiterait le proxy Spring. Passer par un autre bean est ce qui
 * garantit l'isolation de chaque import.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MitreCatalogImportService {

    private final MitreCatalogService catalogService;

    public record ItemFailure(int index, String attackId, String code, String message) {
    }

    public record ImportReport(int received, int created, int updated, List<ItemFailure> rejected) {

        public int rejectedCount() {
            return rejected.size();
        }
    }

    public ImportReport importBatch(List<MitreTechnique.CatalogEntry> entries) {
        int created = 0;
        int updated = 0;
        List<ItemFailure> rejected = new ArrayList<>();

        for (int index = 0; index < entries.size(); index++) {
            MitreTechnique.CatalogEntry entry = entries.get(index);
            try {
                ImportResult result = catalogService.importTechnique(entry);
                if (result.created()) {
                    created++;
                } else {
                    updated++;
                }
            } catch (DomainException e) {
                // Entrée refusée par le domaine (identifiant hors format,
                // aucune tactique, nom manquant…) : nommée, le lot continue.
                rejected.add(new ItemFailure(index, entry.attackId(), e.getCode(), e.getMessage()));
            }
        }

        log.info("ATT&CK import: {} entries -> {} created, {} updated, {} rejected",
                entries.size(), created, updated, rejected.size());
        return new ImportReport(entries.size(), created, updated, List.copyOf(rejected));
    }
}
