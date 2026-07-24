package com.smartsoc.infrastructure.persistence.mitre;

import com.smartsoc.domain.common.PageResult;
import com.smartsoc.domain.mitre.MitreCatalogRepository;
import com.smartsoc.domain.mitre.MitreTechnique;
import com.smartsoc.domain.mitre.MitreTechniqueQuery;
import com.smartsoc.infrastructure.persistence.common.JsonbFunctionContributor;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class MitreCatalogRepositoryAdapter implements MitreCatalogRepository {

    private final SpringDataMitreTechniqueRepository springDataRepository;
    private final MitreTechniqueJpaMapper mapper;

    @Override
    public MitreTechnique save(MitreTechnique technique) {
        return mapper.toDomain(springDataRepository.save(mapper.toJpa(technique)));
    }

    @Override
    public Optional<MitreTechnique> findByAttackId(String normalizedAttackId) {
        return springDataRepository.findByAttackId(normalizedAttackId).map(mapper::toDomain);
    }

    @Override
    public PageResult<MitreTechnique> search(MitreTechniqueQuery query) {
        Specification<MitreTechniqueJpaEntity> spec = Specification.unrestricted();

        if (query.tactic() != null) {
            // Appartenance à un tableau JSONB via containment @> — même
            // forme indexable que la recherche par tag d'un IOC : un appel
            // de fonction n'emprunte jamais un index GIN, seul l'opérateur
            // @> émis par JsonbFunctionContributor le fait.
            String tacticAsJsonArray = jsonArrayOf(query.tactic().name());
            spec = spec.and((root, q, cb) -> cb.isTrue(cb.function(
                    JsonbFunctionContributor.JSONB_ARRAY_CONTAINS, Boolean.class,
                    root.get("tactics"), cb.literal(tacticAsJsonArray))));
        }
        if (!query.includeDeprecated()) {
            spec = spec.and((root, q, cb) -> cb.isFalse(root.get("deprecated")));
        }
        if (query.search() != null && !query.search().isBlank()) {
            String pattern = "%" + query.search().trim().toLowerCase(Locale.ROOT) + "%";
            spec = spec.and((root, q, cb) -> cb.or(
                    cb.like(cb.lower(root.get("attackId")), pattern),
                    cb.like(cb.lower(root.get("name")), pattern),
                    cb.like(cb.lower(root.get("description")), pattern)));
        }

        // Tri : par identifiant ATT&CK croissant (l'ordre naturel de la
        // matrice). Pas d'ORDER BY sur la requête de comptage de la
        // pagination (illégal en JPA).
        spec = spec.and((root, q, cb) -> {
            if (q != null && !Long.class.equals(q.getResultType())) {
                q.orderBy(cb.asc(root.get("attackId")));
            }
            return cb.conjunction();
        });

        PageRequest pageRequest = PageRequest.of(query.page().page(), query.page().size());
        Page<MitreTechniqueJpaEntity> page = springDataRepository.findAll(spec, pageRequest);

        return new PageResult<>(
                page.getContent().stream().map(mapper::toDomain).toList(),
                page.getTotalElements(),
                query.page().page(),
                query.page().size());
    }

    @Override
    public long count() {
        return springDataRepository.count();
    }

    /**
     * La valeur cherchée devient un tableau JSONB d'un seul élément, forme
     * attendue par le containment. L'échappement protège la chaîne JSON.
     */
    private static String jsonArrayOf(String value) {
        String escaped = value.replace("\\", "\\\\").replace("\"", "\\\"");
        return "[\"" + escaped + "\"]";
    }
}
