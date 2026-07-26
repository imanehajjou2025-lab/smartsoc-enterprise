package com.smartsoc.infrastructure.persistence.soar;

import com.smartsoc.domain.common.PageResult;
import com.smartsoc.domain.soar.ExecutionPeriodMetrics;
import com.smartsoc.domain.soar.ExecutionStatus;
import com.smartsoc.domain.soar.PlaybookExecution;
import com.smartsoc.domain.soar.PlaybookExecutionQuery;
import com.smartsoc.domain.soar.PlaybookExecutionRepository;
import com.smartsoc.domain.soar.PlaybookExecutionStep;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class PlaybookExecutionRepositoryAdapter implements PlaybookExecutionRepository {

    private final SpringDataPlaybookExecutionRepository executionRepository;
    private final SpringDataPlaybookExecutionStepRepository stepRepository;
    private final PlaybookExecutionJpaMapper executionMapper;
    private final PlaybookExecutionStepJpaMapper stepMapper;

    @Override
    public PlaybookExecution save(PlaybookExecution execution) {
        return executionMapper.toDomain(executionRepository.save(executionMapper.toJpa(execution)));
    }

    @Override
    public Optional<PlaybookExecution> findById(UUID id) {
        return executionRepository.findById(id).map(executionMapper::toDomain);
    }

    @Override
    public PageResult<PlaybookExecution> search(PlaybookExecutionQuery query) {
        Specification<PlaybookExecutionJpaEntity> spec = Specification.unrestricted();
        if (query.incidentId() != null) {
            spec = spec.and((root, q, cb) -> cb.equal(root.get("incidentId"), query.incidentId()));
        }

        PageRequest pageRequest = PageRequest.of(query.page().page(), query.page().size(),
                Sort.by(Sort.Direction.DESC, "startedAt"));
        Page<PlaybookExecutionJpaEntity> page = executionRepository.findAll(spec, pageRequest);

        return new PageResult<>(
                page.getContent().stream().map(executionMapper::toDomain).toList(),
                page.getTotalElements(), query.page().page(), query.page().size());
    }

    @Override
    public PlaybookExecutionStep saveStep(PlaybookExecutionStep step) {
        return stepMapper.toDomain(stepRepository.save(stepMapper.toJpa(step)));
    }

    @Override
    public Optional<PlaybookExecutionStep> findStepById(UUID stepId) {
        return stepRepository.findById(stepId).map(stepMapper::toDomain);
    }

    @Override
    public List<PlaybookExecutionStep> findSteps(UUID executionId) {
        return stepRepository.findByExecutionIdOrderByStepOrder(executionId).stream()
                .map(stepMapper::toDomain)
                .toList();
    }

    @Override
    public ExecutionPeriodMetrics periodMetrics(Instant from, Instant to) {
        Map<ExecutionStatus, Long> byStatus = new EnumMap<>(ExecutionStatus.class);
        for (Object[] row : executionRepository.countGroupedByStatusInPeriod(from, to)) {
            byStatus.put((ExecutionStatus) row[0], (Long) row[1]);
        }
        long started = byStatus.values().stream().mapToLong(Long::longValue).sum();
        return new ExecutionPeriodMetrics(started,
                byStatus.getOrDefault(ExecutionStatus.COMPLETED, 0L),
                byStatus.getOrDefault(ExecutionStatus.CANCELLED, 0L));
    }
}
