package com.smartsoc.infrastructure.persistence.hunting;

import com.smartsoc.domain.common.PageResult;
import com.smartsoc.domain.hunting.HuntQuery;
import com.smartsoc.domain.hunting.HuntQueryFilter;
import com.smartsoc.domain.hunting.HuntQueryRepository;
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
public class HuntQueryRepositoryAdapter implements HuntQueryRepository {

    private final SpringDataHuntQueryRepository springDataRepository;
    private final HuntQueryJpaMapper mapper;

    @Override
    public HuntQuery save(HuntQuery query) {
        return mapper.toDomain(springDataRepository.save(mapper.toJpa(query)));
    }

    @Override
    public Optional<HuntQuery> findById(UUID id) {
        return springDataRepository.findById(id).map(mapper::toDomain);
    }

    @Override
    public void deleteById(UUID id) {
        springDataRepository.deleteById(id);
    }

    @Override
    public PageResult<HuntQuery> search(HuntQueryFilter filter) {
        Specification<HuntQueryJpaEntity> spec = Specification.unrestricted();
        if (filter.search() != null && !filter.search().isBlank()) {
            String pattern = "%" + filter.search().trim().toLowerCase(Locale.ROOT) + "%";
            spec = spec.and((root, q, cb) -> cb.like(cb.lower(root.get("name")), pattern));
        }

        // Tri : dernière exécution la plus récente d'abord, jamais
        // exécutées en dernier (NULLS LAST), même ordre que l'index
        // ix_hunt_queries_last_executed. Pas d'ORDER BY sur la requête de
        // comptage de la pagination (illégal en JPA).
        spec = spec.and((root, q, cb) -> {
            if (q != null && !Long.class.equals(q.getResultType())) {
                q.orderBy(
                        cb.asc(cb.<Integer>selectCase()
                                .when(cb.isNull(root.get("lastExecutedAt")), 1)
                                .otherwise(0)),
                        cb.desc(root.get("lastExecutedAt")));
            }
            return cb.conjunction();
        });

        PageRequest pageRequest = PageRequest.of(filter.page().page(), filter.page().size());
        Page<HuntQueryJpaEntity> page = springDataRepository.findAll(spec, pageRequest);

        return new PageResult<>(
                page.getContent().stream().map(mapper::toDomain).toList(),
                page.getTotalElements(),
                filter.page().page(),
                filter.page().size());
    }

    @Override
    public long countExecutedInPeriod(Instant from, Instant to) {
        return springDataRepository.countByLastExecutedAtGreaterThanEqualAndLastExecutedAtLessThan(from, to);
    }
}
