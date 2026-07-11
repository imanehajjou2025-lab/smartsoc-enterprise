package com.smartsoc.infrastructure.persistence.alerts;

import com.smartsoc.domain.alerts.Alert;
import com.smartsoc.domain.alerts.AlertQuery;
import com.smartsoc.domain.alerts.AlertRepository;
import com.smartsoc.domain.common.PageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class AlertRepositoryAdapter implements AlertRepository {

    private final SpringDataAlertRepository springDataRepository;
    private final AlertJpaMapper mapper;

    @Override
    public Alert save(Alert alert) {
        return mapper.toDomain(springDataRepository.save(mapper.toJpa(alert)));
    }

    @Override
    public Optional<Alert> findById(UUID id) {
        return springDataRepository.findById(id).map(mapper::toDomain);
    }

    @Override
    public Optional<Alert> findBySourceAndExternalId(String source, String externalId) {
        return springDataRepository.findBySourceAndExternalId(source, externalId)
                .map(mapper::toDomain);
    }

    @Override
    public PageResult<Alert> search(AlertQuery query) {
        Specification<AlertJpaEntity> spec = Specification.unrestricted();
        if (query.status() != null) {
            spec = spec.and((root, q, cb) -> cb.equal(root.get("status"), query.status()));
        }
        if (query.severity() != null) {
            spec = spec.and((root, q, cb) -> cb.equal(root.get("severity"), query.severity()));
        }
        if (query.source() != null && !query.source().isBlank()) {
            spec = spec.and((root, q, cb) ->
                    cb.equal(root.get("source"), query.source().trim().toLowerCase()));
        }

        PageRequest pageRequest = PageRequest.of(query.page().page(), query.page().size(),
                Sort.by(Sort.Direction.DESC, "detectedAt"));
        Page<AlertJpaEntity> page = springDataRepository.findAll(spec, pageRequest);

        return new PageResult<>(
                page.getContent().stream().map(mapper::toDomain).toList(),
                page.getTotalElements(),
                query.page().page(),
                query.page().size());
    }
}
