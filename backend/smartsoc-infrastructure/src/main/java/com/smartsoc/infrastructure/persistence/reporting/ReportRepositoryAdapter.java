package com.smartsoc.infrastructure.persistence.reporting;

import com.smartsoc.domain.common.PageResult;
import com.smartsoc.domain.reporting.Report;
import com.smartsoc.domain.reporting.ReportQuery;
import com.smartsoc.domain.reporting.ReportRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class ReportRepositoryAdapter implements ReportRepository {

    private final SpringDataReportRepository springDataRepository;
    private final ReportJpaMapper mapper;

    @Override
    public Report save(Report report) {
        return mapper.toDomain(springDataRepository.save(mapper.toJpa(report)));
    }

    @Override
    public Optional<Report> findById(UUID id) {
        return springDataRepository.findById(id).map(mapper::toDomain);
    }

    @Override
    public PageResult<Report> search(ReportQuery query) {
        Page<ReportJpaEntity> page = springDataRepository.findAllByOrderByGeneratedAtDesc(
                PageRequest.of(query.page().page(), query.page().size()));

        return new PageResult<>(
                page.getContent().stream().map(mapper::toDomain).toList(),
                page.getTotalElements(), query.page().page(), query.page().size());
    }
}
