package com.smartsoc.infrastructure.persistence.intelligence;

import com.smartsoc.domain.common.PageResult;
import com.smartsoc.domain.intelligence.Indicator;
import com.smartsoc.domain.intelligence.IndicatorQuery;
import com.smartsoc.domain.intelligence.IndicatorRepository;
import com.smartsoc.domain.intelligence.IndicatorType;
import com.smartsoc.infrastructure.persistence.common.JsonbFunctionContributor;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class IndicatorRepositoryAdapter implements IndicatorRepository {

    // Noms d'attributs JPA employés par les Specifications. Nommés une
    // fois : une faute de frappe dans un littéral répété ne se verrait
    // qu'à l'exécution.
    private static final String ATTR_REVOKED = "revoked";
    private static final String ATTR_VALID_UNTIL = "validUntil";

    private final SpringDataIndicatorRepository springDataRepository;
    private final IndicatorJpaMapper mapper;

    @Override
    public Indicator save(Indicator indicator) {
        return mapper.toDomain(springDataRepository.save(mapper.toJpa(indicator)));
    }

    @Override
    public Optional<Indicator> findById(UUID id) {
        return springDataRepository.findById(id).map(mapper::toDomain);
    }

    @Override
    public Optional<Indicator> findByIdentity(IndicatorType type, String normalizedValue) {
        return springDataRepository.findByTypeAndValue(type, normalizedValue)
                .map(mapper::toDomain);
    }

    @Override
    public PageResult<Indicator> search(IndicatorQuery query) {
        Specification<IndicatorJpaEntity> spec = Specification.unrestricted();
        if (query.type() != null) {
            spec = spec.and((root, q, cb) -> cb.equal(root.get("type"), query.type()));
        }
        if (query.feedSource() != null && !query.feedSource().isBlank()) {
            String feedSource = query.feedSource().trim().toLowerCase(Locale.ROOT);
            spec = spec.and((root, q, cb) -> cb.equal(root.get("feedSource"), feedSource));
        }
        if (query.minConfidence() != null) {
            spec = spec.and((root, q, cb) ->
                    cb.greaterThanOrEqualTo(root.get("confidence"), query.minConfidence()));
        }
        if (query.tag() != null && !query.tag().isBlank()) {
            // Appartenance EXACTE à un tableau JSONB : un « like » sur le
            // texte JSON rattacherait « c2 » à un tag « c2-proxy ».
            //
            // Le containment @> est ici préféré à jsonb_exists(tags, :tag)
            // pour une raison mesurée : un appel de FONCTION n'emprunte
            // JAMAIS un index GIN — PostgreSQL ne fait correspondre un
            // index qu'à une expression d'OPÉRATEUR (vérifié : même avec
            // enable_seqscan=off, la forme fonction reste un Seq Scan
            // « Disabled: true », faute d'alternative). Sur 20 002 IOC :
            // 4,053 ms en Seq Scan contre 0,118 ms en Bitmap Index Scan.
            // L'opérateur natif « ? » de PostgreSQL étant inutilisable via
            // JDBC (le caractère entre en conflit avec les paramètres
            // liés), @> est la seule forme à la fois exacte et indexable —
            // émise par la fonction à motif de JsonbFunctionContributor.
            String tag = query.tag().trim().toLowerCase(Locale.ROOT);
            String tagAsJsonArray = jsonArrayOf(tag);
            spec = spec.and((root, q, cb) -> cb.isTrue(cb.function(
                    JsonbFunctionContributor.JSONB_ARRAY_CONTAINS, Boolean.class,
                    root.get("tags"), cb.literal(tagAsJsonArray))));
        }
        if (query.search() != null && !query.search().isBlank()) {
            String pattern = "%" + query.search().trim().toLowerCase(Locale.ROOT) + "%";
            spec = spec.and((root, q, cb) -> cb.or(
                    cb.like(cb.lower(root.get("value")), pattern),
                    cb.like(cb.lower(root.get("description")), pattern)));
        }
        if (query.status() != null) {
            spec = spec.and(statusSpecification(query));
        }

        // Tri : dernière observation d'abord (un IOC frais vaut mieux qu'un
        // IOC ancien). Même garde que pour les actifs : pas d'ORDER BY sur
        // la requête de comptage de la pagination (illégal en JPA).
        spec = spec.and((root, q, cb) -> {
            if (q != null && !Long.class.equals(q.getResultType())) {
                q.orderBy(cb.desc(root.get("lastSeen")));
            }
            return cb.conjunction();
        });

        PageRequest pageRequest = PageRequest.of(query.page().page(), query.page().size());
        Page<IndicatorJpaEntity> page = springDataRepository.findAll(spec, pageRequest);

        return new PageResult<>(
                page.getContent().stream().map(mapper::toDomain).toList(),
                page.getTotalElements(),
                query.page().page(),
                query.page().size());
    }

    /**
     * Le tag cherché devient un tableau JSONB d'un seul élément, forme
     * attendue par le containment. L'échappement protège la valeur : un
     * tag mal formé ne doit pas pouvoir sortir de la chaîne JSON.
     */
    private static String jsonArrayOf(String tag) {
        String escaped = tag.replace("\\", "\\\\").replace("\"", "\\\"");
        return "[\"" + escaped + "\"]";
    }

    /**
     * Le statut est DÉDUIT en SQL, à l'instant de référence porté par la
     * requête — jamais lu dans une colonne, jamais recalculé en Java après
     * coup. Deux conséquences voulues : la liste et son total partagent le
     * même prédicat (donc le compteur ne peut pas diverger de la liste),
     * et aucun batch de péremption n'a besoin d'exister.
     */
    private static Specification<IndicatorJpaEntity> statusSpecification(IndicatorQuery query) {
        Instant at = query.evaluatedAt();
        return switch (query.status()) {
            case REVOKED -> (root, q, cb) -> cb.isTrue(root.get(ATTR_REVOKED));
            case ACTIVE -> (root, q, cb) -> cb.and(
                    cb.isFalse(root.get(ATTR_REVOKED)),
                    cb.or(cb.isNull(root.get(ATTR_VALID_UNTIL)),
                            cb.greaterThan(root.get(ATTR_VALID_UNTIL), at)));
            case EXPIRED -> (root, q, cb) -> cb.and(
                    cb.isFalse(root.get(ATTR_REVOKED)),
                    cb.isNotNull(root.get(ATTR_VALID_UNTIL)),
                    cb.lessThanOrEqualTo(root.get(ATTR_VALID_UNTIL), at));
        };
    }
}
