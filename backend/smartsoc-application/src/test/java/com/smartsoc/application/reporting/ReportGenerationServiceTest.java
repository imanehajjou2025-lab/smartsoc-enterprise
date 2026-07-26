package com.smartsoc.application.reporting;

import com.smartsoc.domain.alerts.AlertPeriodMetrics;
import com.smartsoc.domain.alerts.AlertRepository;
import com.smartsoc.domain.alerts.MitreCoverageCount;
import com.smartsoc.domain.hunting.HuntQueryRepository;
import com.smartsoc.domain.identity.Role;
import com.smartsoc.domain.identity.User;
import com.smartsoc.domain.identity.UserRepository;
import com.smartsoc.domain.incidents.IncidentPeriodMetrics;
import com.smartsoc.domain.incidents.IncidentRepository;
import com.smartsoc.domain.reporting.Report;
import com.smartsoc.domain.reporting.ReportRepository;
import com.smartsoc.domain.soar.ExecutionPeriodMetrics;
import com.smartsoc.domain.soar.PlaybookExecutionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReportGenerationServiceTest {

    private static final Instant START = Instant.parse("2026-07-01T00:00:00Z");
    private static final Instant END = Instant.parse("2026-07-08T00:00:00Z");

    @Mock
    private ReportRepository reportRepository;
    @Mock
    private AlertRepository alertRepository;
    @Mock
    private IncidentRepository incidentRepository;
    @Mock
    private PlaybookExecutionRepository executionRepository;
    @Mock
    private HuntQueryRepository huntQueryRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private ReportNotifier notifier;

    @InjectMocks
    private ReportGenerationService service;

    @Test
    void generateAssemblesMetricsFromEveryContextAndPersistsAnImmutableSnapshot() {
        when(alertRepository.periodMetrics(START, END))
                .thenReturn(new AlertPeriodMetrics(5, java.util.Map.of(), java.util.Map.of()));
        when(incidentRepository.periodMetrics(START, END))
                .thenReturn(new IncidentPeriodMetrics(2, 1, 4.5));
        when(executionRepository.periodMetrics(START, END))
                .thenReturn(new ExecutionPeriodMetrics(3, 2, 1));
        when(huntQueryRepository.countExecutedInPeriod(START, END)).thenReturn(7L);
        when(alertRepository.mitreCoverage()).thenReturn(List.of(
                new MitreCoverageCount("T1110", 4), new MitreCoverageCount("T1059", 2)));
        when(reportRepository.save(any(Report.class))).thenAnswer(c -> c.getArgument(0));
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(
                User.create("admin", "admin@smartsoc.local", "hash", "Admin", Role.ADMIN)));

        Report report = service.generate("Hebdo SOC", START, END, "admin");

        assertThat(report.getMetrics().alerts().total()).isEqualTo(5);
        assertThat(report.getMetrics().incidents().opened()).isEqualTo(2);
        assertThat(report.getMetrics().soar().started()).isEqualTo(3);
        assertThat(report.getMetrics().huntQueriesExecuted()).isEqualTo(7);
        assertThat(report.getMetrics().mitreDistinctTechniquesCovered()).isEqualTo(2);
        assertThat(report.getMetrics().mitreTopTechniques()).extracting(MitreCoverageCount::attackId)
                .containsExactly("T1110", "T1059");

        verify(notifier).notifyGenerated(report, "admin@smartsoc.local");
    }

    @Test
    void generateNeverFailsWhenTheActingUserHasNoResolvableEmail() {
        when(alertRepository.periodMetrics(START, END))
                .thenReturn(new AlertPeriodMetrics(0, java.util.Map.of(), java.util.Map.of()));
        when(incidentRepository.periodMetrics(START, END)).thenReturn(new IncidentPeriodMetrics(0, 0, null));
        when(executionRepository.periodMetrics(START, END)).thenReturn(new ExecutionPeriodMetrics(0, 0, 0));
        when(huntQueryRepository.countExecutedInPeriod(START, END)).thenReturn(0L);
        when(alertRepository.mitreCoverage()).thenReturn(List.of());
        when(reportRepository.save(any(Report.class))).thenAnswer(c -> c.getArgument(0));
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        Report report = service.generate("Hebdo SOC", START, END, "ghost");

        assertThat(report).isNotNull();
        verify(notifier, never()).notifyGenerated(any(), any());
    }
}
