package com.smartsoc.infrastructure.persistence.assets;

import com.smartsoc.domain.assets.Asset;
import com.smartsoc.domain.assets.AssetCriticality;
import com.smartsoc.domain.assets.AssetQuery;
import com.smartsoc.domain.assets.AssetRepository;
import com.smartsoc.domain.common.PageResult;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class AssetRepositoryAdapter implements AssetRepository {

    private final SpringDataAssetRepository springDataRepository;
    private final AssetJpaMapper mapper;

    @Override
    public Asset save(Asset asset) {
        return mapper.toDomain(springDataRepository.save(mapper.toJpa(asset)));
    }

    @Override
    public Optional<Asset> findById(UUID id) {
        return springDataRepository.findById(id).map(mapper::toDomain);
    }

    @Override
    public Optional<Asset> findByHostname(String hostname) {
        return springDataRepository.findByHostname(hostname).map(mapper::toDomain);
    }

    @Override
    public Optional<Asset> findByExternalRef(String externalSource, String externalId) {
        return springDataRepository.findByExternalSourceAndExternalId(externalSource, externalId)
                .map(mapper::toDomain);
    }

    @Override
    public PageResult<Asset> search(AssetQuery query) {
        Specification<AssetJpaEntity> spec = Specification.unrestricted();
        if (query.type() != null) {
            spec = spec.and((root, q, cb) -> cb.equal(root.get("type"), query.type()));
        }
        if (query.criticality() != null) {
            spec = spec.and((root, q, cb) ->
                    cb.equal(root.get("criticality"), query.criticality()));
        }
        if (query.exposure() != null) {
            spec = spec.and((root, q, cb) -> cb.equal(root.get("exposure"), query.exposure()));
        }
        if (query.status() != null) {
            spec = spec.and((root, q, cb) -> cb.equal(root.get("status"), query.status()));
        }
        if (query.search() != null && !query.search().isBlank()) {
            String pattern = "%" + query.search().trim().toLowerCase(Locale.ROOT) + "%";
            spec = spec.and((root, q, cb) -> cb.or(
                    cb.like(root.get("hostname"), pattern),
                    cb.like(cb.lower(root.get("displayName")), pattern)));
        }

        // Tri « criticité la plus haute d'abord » : la colonne est un
        // VARCHAR (lisible en base), son ordre alphabétique est faux
        // (LOW avant MEDIUM) — le rang vient d'un CASE ordinal. La garde
        // sur le type de résultat évite d'imposer un ORDER BY à la
        // requête de comptage de la pagination (illégal en JPA).
        spec = spec.and((root, q, cb) -> {
            if (q != null && !Long.class.equals(q.getResultType())) {
                q.orderBy(cb.asc(criticalityRank(cb, root.get("criticality"))),
                        cb.asc(root.get("hostname")));
            }
            return cb.conjunction();
        });

        PageRequest pageRequest = PageRequest.of(query.page().page(), query.page().size());
        Page<AssetJpaEntity> page = springDataRepository.findAll(spec, pageRequest);

        return new PageResult<>(
                page.getContent().stream().map(mapper::toDomain).toList(),
                page.getTotalElements(),
                query.page().page(),
                query.page().size());
    }

    private static Expression<Integer> criticalityRank(CriteriaBuilder cb,
                                                       Expression<AssetCriticality> criticality) {
        CriteriaBuilder.Case<Integer> rank = cb.selectCase();
        for (AssetCriticality value : AssetCriticality.values()) {
            rank = rank.when(cb.equal(criticality, value), value.ordinal());
        }
        return rank.otherwise(AssetCriticality.values().length);
    }
}
