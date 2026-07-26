package com.smartsoc.application.reporting;

import com.smartsoc.domain.common.PageResult;
import com.smartsoc.domain.common.ResourceNotFoundException;
import com.smartsoc.domain.reporting.Report;
import com.smartsoc.domain.reporting.ReportQuery;
import com.smartsoc.domain.reporting.ReportRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ReportQueryService {

    private final ReportRepository reportRepository;

    @Transactional(readOnly = true)
    public Report get(UUID id) {
        return reportRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Report", id));
    }

    @Transactional(readOnly = true)
    public PageResult<Report> search(ReportQuery query) {
        return reportRepository.search(query);
    }
}
