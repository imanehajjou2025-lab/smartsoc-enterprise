package com.smartsoc.infrastructure.persistence.intelligence;

import com.smartsoc.domain.common.PageResult;
import com.smartsoc.domain.intelligence.Indicator;
import com.smartsoc.domain.intelligence.IndicatorQuery;
import com.smartsoc.domain.intelligence.IndicatorRepository;
import com.smartsoc.domain.intelligence.IndicatorType;
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
            // LIMITE MESURÉE : cette forme FONCTION ne peut pas emprunter
            // l'index GIN. PostgreSQL ne fait correspondre un index qu'à
            // une expression d'OPÉRATEUR, jamais à l'appel de fonction
            // équivalent — vérifié : même avec enable_seqscan=off le plan
            // reste un Seq Scan « Disabled: true ». Sur 20 002 IOC :
            // 4,05 ms ici contre 0,118 ms pour « tags @> '[...]' » qui,
            // lui, prend l'index (Bitmap Index Scan). L'opérateur @> ne
            // peut pas être émis par l'API Criteria sans enregistrer une
            // fonction Hibernate rendue en motif SQL — traité à part pour
            // ne pas mélanger deux sujets dans le même lot.
            // (L'opérateur natif « ? » est, lui, inutilisable via JDBC :
            // le caractère entre en conflit avec les paramètres liés.)
            String tag = query.tag().trim().toLowerCase(Locale.ROOT);
            spec = spec.and((root, q, cb) -> cb.isTrue(cb.function(
                    "jsonb_exists", Boolean.class, root.get("tags"), cb.literal(tag))));
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
     * Le statut est DÉDUIT en SQL, à l'instant de référence porté par la
     * requête — jamais lu dans une colonne, jamais recalculé en Java après
     * coup. Deux conséquences voulues : la liste et son total partagent le
     * même prédicat (donc le compteur ne peut pas diverger de la liste),
     * et aucun batch de péremption n'a besoin d'exister.
     */
    private static Specification<IndicatorJpaEntity> statusSpecification(IndicatorQuery query) {
        Instant at = query.evaluatedAt();
        return switch (query.status()) {
            case REVOKED -> (root, q, cb) -> cb.isTrue(root.get("revoked"));
            case ACTIVE -> (root, q, cb) -> cb.and(
                    cb.isFalse(root.get("revoked")),
                    cb.or(cb.isNull(root.get("validUntil")),
                            cb.greaterThan(root.get("validUntil"), at)));
            case EXPIRED -> (root, q, cb) -> cb.and(
                    cb.isFalse(root.get("revoked")),
                    cb.isNotNull(root.get("validUntil")),
                    cb.lessThanOrEqualTo(root.get("validUntil"), at));
        };
    }
}
