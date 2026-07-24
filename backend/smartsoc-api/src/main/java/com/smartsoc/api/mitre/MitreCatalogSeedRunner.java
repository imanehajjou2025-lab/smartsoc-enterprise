package com.smartsoc.api.mitre;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartsoc.application.mitre.MitreCatalogImportService;
import com.smartsoc.application.mitre.MitreCatalogImportService.ImportReport;
import com.smartsoc.application.mitre.MitreCatalogService;
import com.smartsoc.domain.mitre.MitreTactic;
import com.smartsoc.domain.mitre.MitreTechnique;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Semis du catalogue ATT&CK au démarrage — l'équivalent du mode
 * « simulation par défaut » de CTI : la plateforme est démoable seule, avec
 * une matrice déjà peuplée, sans dépendre d'un import externe.
 *
 * <p><b>Idempotent</b> : ne sème que si le catalogue est vide. Un opérateur
 * qui a déjà importé un bundle complet (via l'API) n'est jamais écrasé —
 * même garde que le bootstrap de l'administrateur.
 *
 * <p>Désactivable par {@code smartsoc.mitre.seed-on-startup=false} (actif
 * par défaut). La provenance reste externe à la plateforme (ADR-005) : la
 * ressource embarquée est un sous-ensemble ATT&CK versionné, figé dans le
 * dépôt, jamais récupéré du réseau au runtime.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "smartsoc.mitre.seed-on-startup", havingValue = "true", matchIfMissing = true)
public class MitreCatalogSeedRunner implements ApplicationRunner {

    private static final String SEED_RESOURCE = "mitre/mitre-attack-enterprise-seed.json";

    private final MitreCatalogService catalogService;
    private final MitreCatalogImportService importService;
    private final ObjectMapper objectMapper;

    /** La ressource embarquée : un sous-ensemble ATT&CK, tactiques en shortnames STIX. */
    record SeedFile(String attackVersion, List<SeedTechnique> techniques) {
    }

    record SeedTechnique(String attackId, String name, String description, String url,
                         List<String> tactics, boolean deprecated) {
    }

    @Override
    public void run(ApplicationArguments args) {
        long existing = catalogService.count();
        if (existing > 0) {
            log.info("MITRE ATT&CK catalog already populated ({} techniques); skipping seed", existing);
            return;
        }
        SeedFile seed = load();
        List<MitreTechnique.CatalogEntry> entries = seed.techniques().stream()
                .map(technique -> toEntry(technique, seed.attackVersion()))
                .toList();
        ImportReport report = importService.importBatch(entries);
        log.info("Seeded MITRE ATT&CK catalog (v{}): {} created, {} rejected",
                seed.attackVersion(), report.created(), report.rejectedCount());
    }

    private SeedFile load() {
        try (InputStream in = new ClassPathResource(SEED_RESOURCE).getInputStream()) {
            return objectMapper.readValue(in, SeedFile.class);
        } catch (IOException e) {
            // La ressource est empaquetée avec l'application : son absence ou
            // sa corruption est une erreur de build, pas une condition runtime.
            throw new IllegalStateException(
                    "Unable to read the bundled MITRE ATT&CK seed " + SEED_RESOURCE, e);
        }
    }

    private static MitreTechnique.CatalogEntry toEntry(SeedTechnique technique, String attackVersion) {
        // Résolution shortname STIX -> tactique, tolérante : un shortname
        // inconnu est écarté ; s'il ne reste aucune tactique, l'import
        // rejettera l'entrée et la nommera (INVALID_MITRE_TECHNIQUE).
        Set<MitreTactic> tactics = EnumSet.noneOf(MitreTactic.class);
        if (technique.tactics() != null) {
            technique.tactics().stream()
                    .map(MitreTactic::fromShortName)
                    .flatMap(Optional::stream)
                    .forEach(tactics::add);
        }
        return MitreTechnique.CatalogEntry.builder()
                .attackId(technique.attackId())
                .name(technique.name())
                .description(technique.description())
                .url(technique.url())
                .tactics(tactics)
                .deprecated(technique.deprecated())
                .attackVersion(attackVersion)
                .build();
    }
}
