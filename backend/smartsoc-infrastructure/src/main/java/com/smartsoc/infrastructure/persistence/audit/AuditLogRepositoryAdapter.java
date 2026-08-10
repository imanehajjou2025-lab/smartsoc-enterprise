package com.smartsoc.infrastructure.persistence.audit;

import com.smartsoc.domain.audit.AuditLogEntry;
import com.smartsoc.domain.audit.AuditLogQuery;
import com.smartsoc.domain.audit.AuditLogRepository;
import com.smartsoc.domain.common.PageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AuditLogRepositoryAdapter implements AuditLogRepository {

    private final SpringDataAuditLogRepository springDataRepository;
    private final AuditLogJpaMapper mapper;

    @Override
    public AuditLogEntry save(AuditLogEntry entry) {
        return mapper.toDomain(springDataRepository.save(mapper.toJpa(entry)));
    }

    @Override
    public PageResult<AuditLogEntry> search(AuditLogQuery query) {
        Specification<AuditLogJpaEntity> spec = Specification.unrestricted();
        if (query.action() != null) {
            spec = spec.and((root, q, cb) -> cb.equal(root.get("action"), query.action()));
        }
        if (query.actorUsername() != null && !query.actorUsername().isBlank()) {
            spec = spec.and((root, q, cb) ->
                    cb.equal(root.get("actorUsername"), query.actorUsername().trim().toLowerCase()));
        }
        if (query.from() != null) {
            spec = spec.and((root, q, cb) -> cb.greaterThanOrEqualTo(root.get("occurredAt"), query.from()));
        }
        if (query.to() != null) {
            spec = spec.and((root, q, cb) -> cb.lessThanOrEqualTo(root.get("occurredAt"), query.to()));
        }
        if (query.targetType() != null && !query.targetType().isBlank()) {
            spec = spec.and((root, q, cb) -> cb.equal(root.get("targetType"), query.targetType()));
        }
        if (query.targetId() != null && !query.targetId().isBlank()) {
            spec = spec.and((root, q, cb) -> cb.equal(root.get("targetId"), query.targetId()));
        }

        PageRequest pageRequest = PageRequest.of(query.page().page(), query.page().size(),
                Sort.by(Sort.Direction.DESC, "occurredAt"));
        Page<AuditLogJpaEntity> page = springDataRepository.findAll(spec, pageRequest);

        return new PageResult<>(
                page.getContent().stream().map(mapper::toDomain).toList(),
                page.getTotalElements(), query.page().page(), query.page().size());
    }
}
