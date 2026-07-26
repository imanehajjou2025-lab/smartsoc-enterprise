package com.smartsoc.application.reporting;

import com.smartsoc.domain.alerts.AlertPeriodMetrics;
import com.smartsoc.domain.common.PageQuery;
import com.smartsoc.domain.common.PageResult;
import com.smartsoc.domain.common.ResourceNotFoundException;
import com.smartsoc.domain.incidents.IncidentPeriodMetrics;
import com.smartsoc.domain.reporting.Report;
import com.smartsoc.domain.reporting.ReportMetrics;
import com.smartsoc.domain.reporting.ReportQuery;
import com.smartsoc.domain.reporting.ReportRepository;
import com.smartsoc.domain.soar.ExecutionPeriodMetrics;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReportQueryServiceTest {

    @Mock
    private ReportRepository reportRepository;

    @InjectMocks
    private ReportQueryService service;

    private static Report sampleReport() {
        return Report.generate("Hebdo SOC",
                Instant.parse("2026-07-01T00:00:00Z"), Instant.parse("2026-07-08T00:00:00Z"), "admin",
                new ReportMetrics(
                        new AlertPeriodMetrics(0, Map.of(), Map.of()),
                        new IncidentPeriodMetrics(0, 0, null),
                        new ExecutionPeriodMetrics(0, 0, 0),
                        0, 0, List.of()));
    }

    @Test
    void getReturnsThePersistedReport() {
        Report report = sampleReport();
        when(reportRepository.findById(report.getId())).thenReturn(Optional.of(report));

        assertThat(service.get(report.getId())).isEqualTo(report);
    }

    @Test
    void getRaises404WhenTheReportIsMissing() {
        UUID id = UUID.randomUUID();
        when(reportRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(id)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void searchDelegatesToTheRepository() {
        ReportQuery query = new ReportQuery(PageQuery.of(0, 25));
        PageResult<Report> page = new PageResult<>(List.of(sampleReport()), 1, 0, 25);
        when(reportRepository.search(query)).thenReturn(page);

        assertThat(service.search(query)).isEqualTo(page);
    }
}
