package com.smartsoc.infrastructure.persistence.investigations;

import com.smartsoc.domain.common.PageResult;
import com.smartsoc.domain.investigations.Case;
import com.smartsoc.domain.investigations.CaseQuery;
import com.smartsoc.domain.investigations.CaseRepository;
import com.smartsoc.domain.investigations.CaseTask;
import com.smartsoc.domain.investigations.CaseTimelineEntry;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class CaseRepositoryAdapter implements CaseRepository {

    private final SpringDataCaseRepository caseRepository;
    private final SpringDataCaseTaskRepository taskRepository;
    private final SpringDataCaseTimelineRepository timelineRepository;
    private final CaseJpaMapper mapper;

    @Override
    public Case save(Case investigationCase) {
        return mapper.toDomain(caseRepository.save(mapper.toJpa(investigationCase)));
    }

    @Override
    public Optional<Case> findById(UUID id) {
        return caseRepository.findById(id).map(mapper::toDomain);
    }

    @Override
    public Optional<Case> findByReference(String reference) {
        return caseRepository.findByReference(reference).map(mapper::toDomain);
    }

    @Override
    public PageResult<Case> search(CaseQuery query) {
        Specification<CaseJpaEntity> spec = Specification.unrestricted();
        if (query.status() != null) {
            spec = spec.and((root, q, cb) -> cb.equal(root.get("status"), query.status()));
        }
        if (query.priority() != null) {
            spec = spec.and((root, q, cb) -> cb.equal(root.get("priority"), query.priority()));
        }
        if (query.assigneeUsername() != null && !query.assigneeUsername().isBlank()) {
            spec = spec.and((root, q, cb) -> cb.equal(root.get("assigneeUsername"),
                    query.assigneeUsername().trim().toLowerCase()));
        }

        PageRequest pageRequest = PageRequest.of(query.page().page(), query.page().size(),
                Sort.by(Sort.Direction.DESC, "openedAt"));
        Page<CaseJpaEntity> page = caseRepository.findAll(spec, pageRequest);

        return new PageResult<>(
                page.getContent().stream().map(mapper::toDomain).toList(),
                page.getTotalElements(),
                query.page().page(),
                query.page().size());
    }

    @Override
    public List<Case> findFollowUps(UUID originCaseId) {
        return caseRepository.findByOriginCaseIdOrderByOpenedAt(originCaseId).stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    @Transactional
    public void linkIncident(UUID caseId, UUID incidentId) {
        caseRepository.linkIncident(caseId, incidentId);
    }

    @Override
    @Transactional
    public void unlinkIncident(UUID caseId, UUID incidentId) {
        caseRepository.unlinkIncident(caseId, incidentId);
    }

    @Override
    public List<UUID> findLinkedIncidentIds(UUID caseId) {
        return caseRepository.findLinkedIncidentIds(caseId);
    }

    @Override
    @Transactional
    public void linkAlert(UUID caseId, UUID alertId) {
        caseRepository.linkAlert(caseId, alertId);
    }

    @Override
    @Transactional
    public void unlinkAlert(UUID caseId, UUID alertId) {
        caseRepository.unlinkAlert(caseId, alertId);
    }

    @Override
    public List<UUID> findLinkedAlertIds(UUID caseId) {
        return caseRepository.findLinkedAlertIds(caseId);
    }

    @Override
    public CaseTask saveTask(CaseTask task) {
        return mapper.toDomain(taskRepository.save(mapper.toJpa(task)));
    }

    @Override
    public Optional<CaseTask> findTaskById(UUID taskId) {
        return taskRepository.findById(taskId).map(mapper::toDomain);
    }

    @Override
    public List<CaseTask> findTasks(UUID caseId) {
        return taskRepository.findByCaseIdOrderByCreatedAt(caseId).stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    public void addTimelineEntry(CaseTimelineEntry entry) {
        timelineRepository.save(mapper.toJpa(entry));
    }

    @Override
    public List<CaseTimelineEntry> findTimeline(UUID caseId) {
        return timelineRepository.findByCaseIdOrderByOccurredAt(caseId).stream()
                .map(mapper::toDomain)
                .toList();
    }
}
