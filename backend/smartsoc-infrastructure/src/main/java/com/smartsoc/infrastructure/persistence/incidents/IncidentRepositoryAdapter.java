package com.smartsoc.infrastructure.persistence.incidents;

import com.smartsoc.domain.common.PageResult;
import com.smartsoc.domain.incidents.Incident;
import com.smartsoc.domain.incidents.IncidentPeriodMetrics;
import com.smartsoc.domain.incidents.IncidentQuery;
import com.smartsoc.domain.incidents.IncidentRepository;
import com.smartsoc.domain.incidents.IncidentTimelineEntry;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class IncidentRepositoryAdapter implements IncidentRepository {

    private final SpringDataIncidentRepository incidentRepository;
    private final SpringDataIncidentTimelineRepository timelineRepository;
    private final IncidentJpaMapper mapper;

    @Override
    public Incident save(Incident incident) {
        return mapper.toDomain(incidentRepository.save(mapper.toJpa(incident)));
    }

    @Override
    public Optional<Incident> findById(UUID id) {
        return incidentRepository.findById(id).map(mapper::toDomain);
    }

    @Override
    public Optional<Incident> findByReference(String reference) {
        return incidentRepository.findByReference(reference).map(mapper::toDomain);
    }

    @Override
    public PageResult<Incident> search(IncidentQuery query) {
        Specification<IncidentJpaEntity> spec = Specification.unrestricted();
        if (query.status() != null) {
            spec = spec.and((root, q, cb) -> cb.equal(root.get("status"), query.status()));
        }
        if (query.severity() != null) {
            spec = spec.and((root, q, cb) -> cb.equal(root.get("severity"), query.severity()));
        }
        if (query.assigneeUsername() != null && !query.assigneeUsername().isBlank()) {
            spec = spec.and((root, q, cb) -> cb.equal(root.get("assigneeUsername"),
                    query.assigneeUsername().trim().toLowerCase()));
        }

        PageRequest pageRequest = PageRequest.of(query.page().page(), query.page().size(),
                Sort.by(Sort.Direction.DESC, "openedAt"));
        Page<IncidentJpaEntity> page = incidentRepository.findAll(spec, pageRequest);

        return new PageResult<>(
                page.getContent().stream().map(mapper::toDomain).toList(),
                page.getTotalElements(),
                query.page().page(),
                query.page().size());
    }

    @Override
    @Transactional
    public void linkAlert(UUID incidentId, UUID alertId) {
        incidentRepository.linkAlert(incidentId, alertId);
    }

    @Override
    @Transactional
    public void unlinkAlert(UUID incidentId, UUID alertId) {
        incidentRepository.unlinkAlert(incidentId, alertId);
    }

    @Override
    public List<UUID> findLinkedAlertIds(UUID incidentId) {
        return incidentRepository.findLinkedAlertIds(incidentId);
    }

    @Override
    public void addTimelineEntry(IncidentTimelineEntry entry) {
        timelineRepository.save(mapper.toJpa(entry));
    }

    @Override
    public List<IncidentTimelineEntry> findTimeline(UUID incidentId) {
        return timelineRepository.findByIncidentIdOrderByOccurredAt(incidentId).stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    public IncidentPeriodMetrics periodMetrics(Instant from, Instant to) {
        long opened = incidentRepository.countOpenedInPeriod(from, to);
        Object[] closedRow = incidentRepository.closedPeriodStats(from, to).get(0);
        // avg(...) rend un numeric (donc un BigDecimal cote JDBC) des lors
        // que la division porte sur un literal decimal — Number encaisse
        // BigDecimal comme Double sans dependre de ce detail de typage SQL.
        Double avgResolutionHours = closedRow[1] == null ? null : ((Number) closedRow[1]).doubleValue();
        return new IncidentPeriodMetrics(opened, (Long) closedRow[0], avgResolutionHours);
    }
}
