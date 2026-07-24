package com.smartsoc.application.mitre;

import com.smartsoc.domain.common.DuplicateResourceException;
import com.smartsoc.domain.common.PageResult;
import com.smartsoc.domain.common.ResourceNotFoundException;
import com.smartsoc.domain.mitre.MitreCatalogRepository;
import com.smartsoc.domain.mitre.MitreTechnique;
import com.smartsoc.domain.mitre.MitreTechniqueId;
import com.smartsoc.domain.mitre.MitreTechniqueQuery;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cas d'usage du catalogue ATT&CK : consultation et import par élément.
 *
 * <p>L'import d'une technique est un UPSERT sur l'identité (l'identifiant
 * ATT&CK normalisé) — ré-importer une technique déjà connue rafraîchit ses
 * métadonnées sans jamais échouer, exactement comme l'ingestion d'un flux
 * CTI. Le lot tolérant est orchestré à part par
 * {@link MitreCatalogImportService}, pour la même raison qu'en CTI : une
 * transaction par élément.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MitreCatalogService {

    private final MitreCatalogRepository catalogRepository;

    /** Issue d'un import : distingue création et rafraîchissement. */
    public record ImportResult(MitreTechnique technique, boolean created) {
    }

    /**
     * Upsert d'import, sur l'identité (identifiant ATT&CK normalisé). La
     * normalisation est faite ICI avant la recherche : chercher avec la
     * forme brute ne retrouverait pas l'enregistrement existant et créerait
     * un doublon à chaque ré-import.
     */
    @Transactional
    public ImportResult importTechnique(MitreTechnique.CatalogEntry entry) {
        String normalizedId = MitreTechniqueId.normalize(entry.attackId());
        return catalogRepository.findByAttackId(normalizedId)
                .map(existing -> {
                    existing.refreshFrom(entry);
                    return new ImportResult(catalogRepository.save(existing), false);
                })
                .orElseGet(() -> {
                    try {
                        return new ImportResult(
                                catalogRepository.save(MitreTechnique.fromCatalog(entry)), true);
                    } catch (DataIntegrityViolationException e) {
                        throw new DuplicateResourceException("MITRE_TECHNIQUE_CONFLICT",
                                "Technique %s was imported concurrently; retry".formatted(normalizedId));
                    }
                });
    }

    @Transactional(readOnly = true)
    public PageResult<MitreTechnique> search(MitreTechniqueQuery query) {
        return catalogRepository.search(query);
    }

    @Transactional(readOnly = true)
    public MitreTechnique getTechnique(String attackId) {
        String normalizedId = MitreTechniqueId.normalize(attackId);
        return catalogRepository.findByAttackId(normalizedId)
                .orElseThrow(() -> new ResourceNotFoundException("MitreTechnique", normalizedId));
    }

    @Transactional(readOnly = true)
    public long count() {
        return catalogRepository.count();
    }
}
