package com.smartsoc.api.reporting;

import com.smartsoc.TestcontainersConfiguration;
import com.smartsoc.domain.alerts.AlertPeriodMetrics;
import com.smartsoc.domain.alerts.AlertStatus;
import com.smartsoc.domain.alerts.MitreCoverageCount;
import com.smartsoc.domain.alerts.Severity;
import com.smartsoc.domain.incidents.IncidentPeriodMetrics;
import com.smartsoc.domain.reporting.Report;
import com.smartsoc.domain.reporting.ReportMetrics;
import com.smartsoc.domain.reporting.ReportRepository;
import com.smartsoc.domain.soar.ExecutionPeriodMetrics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Persistance du rapport sur PostgreSQL réel : l'aller-retour JSONB de
 * {@code ReportMetrics} (record plat composé de records plats, aucune
 * hiérarchie scellée) via Jackson natif, sans codec dédié — même choix
 * que {@code Playbook.steps}.
 */
@SpringBootTest(properties = "smartsoc.security.bootstrap-admin.password=IntegrationTest123!")
@Import(TestcontainersConfiguration.class)
class ReportPersistenceIntegrationTest {

    @Autowired
    private ReportRepository repository;

    @Test
    void savesAndReloadsTheFullMetricsSnapshot() {
        ReportMetrics metrics = new ReportMetrics(
                new AlertPeriodMetrics(5, Map.of(Severity.CRITICAL, 3L, Severity.HIGH, 2L),
                        Map.of(AlertStatus.NEW, 5L)),
                new IncidentPeriodMetrics(2, 1, 4.5),
                new ExecutionPeriodMetrics(3, 2, 1),
                7, 2, List.of(new MitreCoverageCount("T1110", 4), new MitreCoverageCount("T1059", 2)));
        Report report = Report.generate("Hebdo SOC",
                Instant.parse("2026-07-01T00:00:00Z"), Instant.parse("2026-07-08T00:00:00Z"),
                "admin", metrics);

        Report saved = repository.save(report);
        Report reloaded = repository.findById(saved.getId()).orElseThrow();

        assertThat(reloaded.getMetrics()).isEqualTo(metrics);
        assertThat(reloaded.getTitle()).isEqualTo("Hebdo SOC");
        assertThat(reloaded.getGeneratedBy()).isEqualTo("admin");
    }
}
