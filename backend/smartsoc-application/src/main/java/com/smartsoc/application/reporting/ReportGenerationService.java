package com.smartsoc.application.reporting;

import com.smartsoc.domain.alerts.AlertPeriodMetrics;
import com.smartsoc.domain.alerts.AlertRepository;
import com.smartsoc.domain.alerts.MitreCoverageCount;
import com.smartsoc.domain.hunting.HuntQueryRepository;
import com.smartsoc.domain.identity.UserRepository;
import com.smartsoc.domain.incidents.IncidentPeriodMetrics;
import com.smartsoc.domain.incidents.IncidentRepository;
import com.smartsoc.domain.reporting.Report;
import com.smartsoc.domain.reporting.ReportMetrics;
import com.smartsoc.domain.reporting.ReportRepository;
import com.smartsoc.domain.soar.ExecutionPeriodMetrics;
import com.smartsoc.domain.soar.PlaybookExecutionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Génère un instantané de rapport en lisant, en lecture seule, à travers
 * les contextes existants (alerts, incidents, soar, hunting, mitre) — même
 * doctrine de lecture inter-contextes que {@code MitreCorrelationService}.
 * Aucun état n'est modifié ailleurs : générer un rapport n'a aucun effet
 * de bord sur les autres modules.
 */
@Service
@RequiredArgsConstructor
public class ReportGenerationService {

    private static final int MITRE_TOP_TECHNIQUES = 5;

    private final ReportRepository reportRepository;
    private final AlertRepository alertRepository;
    private final IncidentRepository incidentRepository;
    private final PlaybookExecutionRepository executionRepository;
    private final HuntQueryRepository huntQueryRepository;
    private final UserRepository userRepository;
    private final ReportNotifier notifier;

    @Transactional
    public Report generate(String title, Instant periodStart, Instant periodEnd, String generatedByUsername) {
        AlertPeriodMetrics alerts = alertRepository.periodMetrics(periodStart, periodEnd);
        IncidentPeriodMetrics incidents = incidentRepository.periodMetrics(periodStart, periodEnd);
        ExecutionPeriodMetrics soar = executionRepository.periodMetrics(periodStart, periodEnd);
        long huntQueriesExecuted = huntQueryRepository.countExecutedInPeriod(periodStart, periodEnd);
        List<MitreCoverageCount> coverage = alertRepository.mitreCoverage();

        ReportMetrics metrics = new ReportMetrics(
                alerts, incidents, soar,
                huntQueriesExecuted,
                coverage.size(),
                coverage.stream().limit(MITRE_TOP_TECHNIQUES).toList());

        Report report = reportRepository.save(
                Report.generate(title, periodStart, periodEnd, generatedByUsername, metrics));

        userRepository.findByUsername(generatedByUsername)
                .ifPresent(user -> notifier.notifyGenerated(report, user.getEmail()));

        return report;
    }
}
